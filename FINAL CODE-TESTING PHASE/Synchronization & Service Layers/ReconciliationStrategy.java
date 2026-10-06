package com.atm.synchronization;

import com.atm.dao.AccountDAO;
import com.atm.dao.OfflineTransactionDAO;
import com.atm.dao.TransactionDAO;
import com.atm.exception.SynchronizationConflictException;
import com.atm.model.Account;
import com.atm.model.OfflineTransaction;
import com.atm.model.Transaction;
import com.atm.model.TransactionStatus;

import java.sql.Connection;
import java.sql.SQLException;

public class ReconciliationStrategy {
    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final OfflineTransactionDAO offlineDAO = new OfflineTransactionDAO();

    public boolean reconcile(OfflineTransaction tx, Connection conn)
            throws SQLException, SynchronizationConflictException {

        // Use the same central transaction/connection for the version check and
        // update so another ATM cannot slip a change between the operations.
        Account centralAccount = accountDAO.getAccountByNumber(tx.getAccountNumber(), conn);

        if (centralAccount == null) {
            throw new SynchronizationConflictException("Account does not exist centrally.");
        }

        if (centralAccount.getVersion() != tx.getBaseVersion()) {
            offlineDAO.logConflict(
                    tx.getTransactionId(),
                    tx.getAccountNumber(),
                    tx.getBaseVersion(),
                    centralAccount.getVersion(),
                    "Stale version mismatch detected.");
            throw new SynchronizationConflictException(
                    "Conflict: Local base version (" + tx.getBaseVersion()
                            + ") != Central version (" + centralAccount.getVersion() + ")");
        }

        double delta = tx.getResultingBalance() - tx.getPreviousBalance();
        centralAccount.setBalance(centralAccount.getBalance() + delta);

        if (!accountDAO.updateAccountWithVersion(centralAccount, conn)) {
            throw new SynchronizationConflictException(
                    "Concurrent update detected on target account during commit.");
        }

        Transaction syncTx = new Transaction(
                tx.getTransactionId(),
                tx.getAccountNumber(),
                tx.getType(),
                tx.getAmount(),
                tx.getTimestamp(),
                centralAccount.getBalance() - delta,
                centralAccount.getBalance(),
                TransactionStatus.SUCCESS);

        transactionDAO.insertTransaction(syncTx, conn);
        return true;
    }
}
