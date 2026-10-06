package com.atm.dao;

import com.atm.infrastructure.DatabaseConnection;
import com.atm.model.Account;
import com.atm.model.CurrentAccount;
import com.atm.model.SavingsAccount;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AccountDAO {

    private static final String ACCOUNT_SELECT =
            "SELECT a.*, COALESCE(("
            + "SELECT SUM(t.amount) FROM transactions t "
            + "WHERE t.account_number = a.account_number "
            + "AND t.type = 'WITHDRAWAL' "
            + "AND t.status = 'SUCCESS' "
            + "AND DATE(t.timestamp) = CURRENT_DATE()"
            + "), 0) AS withdrawn_today "
            + "FROM accounts a ";

    public Account getAccountByNumber(String accountNumber) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return getAccountByNumber(accountNumber, conn);
        }
    }

    public Account getAccountByNumber(String accountNumber, Connection conn) throws SQLException {
        String sql = ACCOUNT_SELECT + "WHERE a.account_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAccount(rs);
                }
            }
        }
        return null;
    }

    public List<Account> getAllAccounts() throws SQLException {
        List<Account> accounts = new ArrayList<>();
        String sql = ACCOUNT_SELECT + "ORDER BY a.account_number";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(mapResultSetToAccount(rs));
            }
        }
        return accounts;
    }

    public boolean updateAccountWithVersion(Account account, Connection conn) throws SQLException {
        String sql = "UPDATE accounts SET balance = ?, version = version + 1 "
                + "WHERE account_number = ? AND version = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, account.getBalance());
            ps.setString(2, account.getAccountNumber());
            ps.setInt(3, account.getVersion());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                account.incrementVersion();
                return true;
            }
            return false;
        }
    }

    public boolean updateAccount(Account account) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return updateAccountWithVersion(account, conn);
        }
    }

    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        String type = rs.getString("account_type");
        String accNum = rs.getString("account_number");
        int custId = rs.getInt("customer_id");
        double balance = rs.getDouble("balance");
        int version = rs.getInt("version");
        double dailyLimit = rs.getDouble("daily_limit");

        Account account;
        if ("SAVINGS".equalsIgnoreCase(type)) {
            double minBalance = rs.getDouble("minimum_balance");
            account = new SavingsAccount(accNum, custId, balance, version, dailyLimit, minBalance);
        } else {
            double overdraftLimit = rs.getDouble("overdraft_limit");
            account = new CurrentAccount(accNum, custId, balance, version, dailyLimit, overdraftLimit);
        }

        account.setWithdrawnToday(rs.getDouble("withdrawn_today"));
        return account;
    }
}
