package com.atm.dao;

import com.atm.infrastructure.DatabaseConnection;
import com.atm.model.Transaction;
import com.atm.model.TransactionStatus;
import com.atm.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    public void insertTransaction(Transaction tx, Connection conn) throws SQLException {
        String sql = "INSERT INTO transactions (transaction_id, account_number, type, amount, timestamp, previous_balance, resulting_balance, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tx.getTransactionId());
            ps.setString(2, tx.getAccountNumber());
            ps.setString(3, tx.getType().name());
            ps.setDouble(4, tx.getAmount());
            ps.setTimestamp(5, tx.getTimestamp());
            ps.setDouble(6, tx.getPreviousBalance());
            ps.setDouble(7, tx.getResultingBalance());
            ps.setString(8, tx.getStatus().name());
            ps.executeUpdate();
        }
    }

    public List<Transaction> getTransactionsByAccount(String accountNumber) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE account_number = ? ORDER BY timestamp DESC LIMIT 10";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Transaction(
                        rs.getString("transaction_id"),
                        rs.getString("account_number"),
                        TransactionType.valueOf(rs.getString("type")),
                        rs.getDouble("amount"),
                        rs.getTimestamp("timestamp"),
                        rs.getDouble("previous_balance"),
                        rs.getDouble("resulting_balance"),
                        TransactionStatus.valueOf(rs.getString("status"))
                    ));
                }
            }
        }
        return list;
    }
}