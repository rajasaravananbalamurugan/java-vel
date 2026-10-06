package com.atm.service;

import com.atm.dao.AccountDAO;
import com.atm.dao.OfflineTransactionDAO;
import com.atm.dao.TransactionDAO;
import com.atm.exception.*;
import com.atm.infrastructure.ATMConfig;
import com.atm.infrastructure.DatabaseConnection;
import com.atm.infrastructure.LVDS;
import com.atm.infrastructure.NetworkManager;
import com.atm.model.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class TransactionService {
    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final OfflineTransactionDAO offlineDAO = new OfflineTransactionDAO();
    private final LVDS lvds = LVDS.getInstance();

    private String newTransactionId() {
        return ATMConfig.getAtmId() + "-TX-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public void executeWithdrawal(Account account, double amount)
            throws InsufficientFundsException, DailyLimitExceededException,
                   SQLException, TransactionFailedException {

        boolean online = NetworkManager.isOnline();
        Account working = prepareAccountForOperation(account, online);

        if (!online) {
            double pendingOfflineWithdrawals =
                    offlineDAO.getTodayWithdrawalTotal(working.getAccountNumber());
            working.setWithdrawnToday(
                    working.getWithdrawnToday() + pendingOfflineWithdrawals);
        }

        double prevBalance = working.getBalance();
        int prevVersion = working.getVersion();
        int baseVersion = prevVersion;

        working.withdraw(amount);
        double newBalance = working.getBalance();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String txId = newTransactionId();

        if (online) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    if (!accountDAO.updateAccountWithVersion(working, conn)) {
                        throw new TransactionFailedException(
                                "Concurrent update conflict in central DB. Please retry.");
                    }

                    Transaction tx = new Transaction(
                            txId, working.getAccountNumber(), TransactionType.WITHDRAWAL,
                            amount, now, prevBalance, newBalance, TransactionStatus.SUCCESS);
                    transactionDAO.insertTransaction(tx, conn);
                    conn.commit();

                    copyState(working, account);
                    lvds.updateLocalAccount(working);
                    System.out.println("[ONLINE] Withdrawal completed successfully.");
                } catch (Exception e) {
                    conn.rollback();
                    working.setBalance(prevBalance);
                    working.setVersion(prevVersion);
                    copyState(working, account);
                    throw new TransactionFailedException(
                            "Online operation failed: " + e.getMessage());
                }
            }
        } else {
            OfflineTransaction offTx = new OfflineTransaction(
                    txId, working.getAccountNumber(), TransactionType.WITHDRAWAL,
                    amount, now, prevBalance, newBalance, baseVersion,
                    TransactionStatus.PENDING_SYNC);

            lvds.updateLocalAccount(working);
            offlineDAO.insertOfflineTransaction(offTx);

            // Local version advances independently while the transaction waits
            // for central synchronization.
            working.incrementVersion();
            lvds.updateLocalAccount(working);
            copyState(working, account);

            System.out.println(
                    "[OFFLINE MODE] Processed locally via LVDS. "
                            + "Transaction recorded for later synchronization.");
        }
    }

    public void executeDeposit(Account account, double amount)
            throws SQLException, TransactionFailedException {

        boolean online = NetworkManager.isOnline();
        Account working = prepareAccountForOperation(account, online);

        double prevBalance = working.getBalance();
        int prevVersion = working.getVersion();
        int baseVersion = prevVersion;

        working.deposit(amount);
        double newBalance = working.getBalance();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String txId = newTransactionId();

        if (online) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    if (!accountDAO.updateAccountWithVersion(working, conn)) {
                        throw new TransactionFailedException(
                                "Concurrent update conflict in central DB. Please retry.");
                    }

                    Transaction tx = new Transaction(
                            txId, working.getAccountNumber(), TransactionType.DEPOSIT,
                            amount, now, prevBalance, newBalance, TransactionStatus.SUCCESS);
                    transactionDAO.insertTransaction(tx, conn);
                    conn.commit();

                    copyState(working, account);
                    lvds.updateLocalAccount(working);
                    System.out.println("[ONLINE] Deposit successful.");
                } catch (Exception e) {
                    conn.rollback();
                    working.setBalance(prevBalance);
                    working.setVersion(prevVersion);
                    copyState(working, account);
                    throw new TransactionFailedException(
                            "Online deposit failed: " + e.getMessage());
                }
            }
        } else {
            OfflineTransaction offTx = new OfflineTransaction(
                    txId, working.getAccountNumber(), TransactionType.DEPOSIT,
                    amount, now, prevBalance, newBalance, baseVersion,
                    TransactionStatus.PENDING_SYNC);

            lvds.updateLocalAccount(working);
            offlineDAO.insertOfflineTransaction(offTx);

            working.incrementVersion();
            lvds.updateLocalAccount(working);
            copyState(working, account);

            System.out.println("[OFFLINE MODE] Deposit recorded locally via LVDS.");
        }
    }

    public void executeTransfer(Account sourceAccount, String targetAccNumber, double amount)
            throws InsufficientFundsException, DailyLimitExceededException,
                   AccountNotFoundException, NetworkUnavailableException,
                   SQLException, TransactionFailedException {

        if (sourceAccount.getAccountNumber().equalsIgnoreCase(targetAccNumber)) {
            throw new IllegalArgumentException("Cannot transfer funds to the same account.");
        }

        if (!NetworkManager.isOnline()) {
            throw new NetworkUnavailableException(
                    "Fund Transfers are strictly disabled in OFFLINE mode for security.");
        }

        // Always load the latest central state for both accounts. This is what
        // makes a transfer from ATM-01 safe when ATM-02 has just changed either
        // account.
        Account source = accountDAO.getAccountByNumber(sourceAccount.getAccountNumber());
        Account target = accountDAO.getAccountByNumber(targetAccNumber);

        if (source == null) {
            throw new AccountNotFoundException("Source account is no longer available.");
        }
        if (target == null) {
            throw new AccountNotFoundException("Target transfer account does not exist.");
        }

        double sourcePrev = source.getBalance();
        double targetPrev = target.getBalance();
        int sourceVersion = source.getVersion();
        int targetVersion = target.getVersion();

        source.withdraw(amount);
        target.deposit(amount);

        String txId = newTransactionId();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (!accountDAO.updateAccountWithVersion(source, conn)
                        || !accountDAO.updateAccountWithVersion(target, conn)) {
                    throw new TransactionFailedException(
                            "Concurrency failure during fund transfer. Please retry.");
                }

                Transaction txSource = new Transaction(
                        txId + "-S", source.getAccountNumber(), TransactionType.TRANSFER,
                        amount, now, sourcePrev, source.getBalance(), TransactionStatus.SUCCESS);

                Transaction txTarget = new Transaction(
                        txId + "-T", target.getAccountNumber(), TransactionType.TRANSFER,
                        amount, now, targetPrev, target.getBalance(), TransactionStatus.SUCCESS);

                transactionDAO.insertTransaction(txSource, conn);
                transactionDAO.insertTransaction(txTarget, conn);
                conn.commit();

                copyState(source, sourceAccount);
                lvds.updateLocalAccount(source);

                System.out.println("[ONLINE] Transfer successful.");
            } catch (Exception e) {
                conn.rollback();
                source.setBalance(sourcePrev);
                source.setVersion(sourceVersion);
                target.setBalance(targetPrev);
                target.setVersion(targetVersion);
                throw new TransactionFailedException(
                        "Transfer failed and was rolled back: " + e.getMessage());
            }
        }
    }

    public List<Transaction> getMiniStatement(String accountNumber)
            throws NetworkUnavailableException, SQLException {

        if (!NetworkManager.isOnline()) {
            throw new NetworkUnavailableException(
                    "Mini Statement requires central database access.");
        }
        return transactionDAO.getTransactionsByAccount(accountNumber);
    }

    /**
     * Returns the current account state:
     * - central snapshot when ONLINE
     * - this ATM's LVDS snapshot when OFFLINE
     */
    public Account getCurrentAccount(Account sessionAccount) throws SQLException {
        if (sessionAccount == null) {
            return null;
        }

        if (NetworkManager.isOnline()) {
            Account central = accountDAO.getAccountByNumber(sessionAccount.getAccountNumber());
            if (central != null) {
                copyState(central, sessionAccount);
                lvds.updateLocalAccount(central);
                return central;
            }
            return null;
        }

        try {
            return lvds.getAccount(sessionAccount.getAccountNumber());
        } catch (java.io.IOException e) {
            throw new SQLException("Unable to read local LVDS account.", e);
        }
    }

    private Account prepareAccountForOperation(Account sessionAccount, boolean online)
            throws SQLException {

        if (online) {
            Account central = accountDAO.getAccountByNumber(sessionAccount.getAccountNumber());
            if (central == null) {
                throw new SQLException("Account no longer exists in central server.");
            }
            copyState(central, sessionAccount);
            return sessionAccount;
        }

        try {
            Account local = lvds.getAccount(sessionAccount.getAccountNumber());
            if (local == null) {
                throw new SQLException("Account is not available in local LVDS.");
            }
            copyState(local, sessionAccount);
            return sessionAccount;
        } catch (java.io.IOException e) {
            throw new SQLException("Unable to read local LVDS account.", e);
        }
    }

    private void copyState(Account source, Account target) {
        target.setBalance(source.getBalance());
        target.setVersion(source.getVersion());
        target.setWithdrawnToday(source.getWithdrawnToday());
    }
}
