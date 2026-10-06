package com.atm.dao;

import com.atm.infrastructure.LVDS;
import com.atm.model.OfflineTransaction;
import com.atm.model.TransactionStatus;
import com.atm.model.TransactionType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Local-only persistence for offline transactions.
 *
 * This DAO intentionally does NOT use the central MySQL connection.
 * Every ATM writes to its own local-lvds/<ATM-ID>/offline_transactions.log.
 */
public class OfflineTransactionDAO {
    private static final String DELIMITER = "|";

    private Path journalPath() {
        return LVDS.getInstance().getRootDirectory()
                .resolve("offline_transactions.log");
    }

    private Path conflictPath() {
        return LVDS.getInstance().getRootDirectory()
                .resolve("sync_conflicts.log");
    }

    public synchronized void insertOfflineTransaction(OfflineTransaction tx) throws SQLException {
        String line = serialize(tx) + System.lineSeparator();
        try {
            Files.createDirectories(journalPath().getParent());
            Files.writeString(journalPath(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw sql("Unable to persist offline transaction locally: " + e.getMessage(), e);
        }
    }

    public synchronized List<OfflineTransaction> getPendingTransactions() throws SQLException {
        List<OfflineTransaction> list = new ArrayList<>();
        if (!Files.exists(journalPath())) return list;

        try {
            for (String line : Files.readAllLines(journalPath(), StandardCharsets.UTF_8)) {
                OfflineTransaction tx = deserialize(line);
                if (tx != null && tx.getSyncStatus() == TransactionStatus.PENDING_SYNC) {
                    list.add(tx);
                }
            }
        } catch (IOException e) {
            throw sql("Unable to read local offline journal: " + e.getMessage(), e);
        }
        return list;
    }

    public synchronized void updateSyncStatus(String txId, TransactionStatus status) throws SQLException {
        if (!Files.exists(journalPath())) return;

        List<String> output = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(journalPath(), StandardCharsets.UTF_8)) {
                OfflineTransaction tx = deserialize(line);
                if (tx != null && tx.getTransactionId().equals(txId)) {
                    tx.setSyncStatus(status);
                    output.add(serialize(tx));
                } else if (!line.isBlank()) {
                    output.add(line);
                }
            }

            Path temp = journalPath().resolveSibling(journalPath().getFileName() + ".tmp");
            Files.write(temp, output, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, journalPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw sql("Unable to update local journal status: " + e.getMessage(), e);
        }
    }

    public synchronized double getTodayWithdrawalTotal(String accountNumber) throws SQLException {
        double total = 0.0;
        if (!Files.exists(journalPath())) return total;

        java.time.LocalDate today = java.time.LocalDate.now();
        try {
            for (String line : Files.readAllLines(journalPath(), StandardCharsets.UTF_8)) {
                OfflineTransaction tx = deserialize(line);
                if (tx != null
                        && tx.getAccountNumber().equalsIgnoreCase(accountNumber)
                        && tx.getType() == TransactionType.WITHDRAWAL
                        && !tx.getTimestamp().toLocalDateTime().toLocalDate().isBefore(today)
                        && tx.getSyncStatus() == TransactionStatus.PENDING_SYNC) {
                    total += tx.getAmount();
                }
            }
        } catch (IOException e) {
            throw sql("Unable to read local withdrawal history: " + e.getMessage(), e);
        }
        return total;
    }

    public synchronized void logConflict(String txId, String accNum, int expected,
                                          int actual, String reason) throws SQLException {
        String line = String.format(
                "TX-ID=%s | ACCOUNT=%s | EXPECTED_VERSION=%d | ACTUAL_VERSION=%d | REASON=%s%n",
                txId, accNum, expected, actual, reason);
        try {
            Files.createDirectories(conflictPath().getParent());
            Files.writeString(conflictPath(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw sql("Unable to persist local conflict record: " + e.getMessage(), e);
        }
    }

    private String serialize(OfflineTransaction tx) {
        return String.join(DELIMITER,
                tx.getTransactionId(),
                tx.getAccountNumber(),
                tx.getType().name(),
                Double.toString(tx.getAmount()),
                tx.getTimestamp().toString(),
                Double.toString(tx.getPreviousBalance()),
                Double.toString(tx.getResultingBalance()),
                Integer.toString(tx.getBaseVersion()),
                tx.getSyncStatus().name());
    }

    private OfflineTransaction deserialize(String line) {
        if (line == null || line.isBlank()) return null;
        String[] parts = line.split("\\|", -1);
        if (parts.length != 9) return null;

        try {
            return new OfflineTransaction(
                    parts[0],
                    parts[1],
                    TransactionType.valueOf(parts[2]),
                    Double.parseDouble(parts[3]),
                    Timestamp.valueOf(parts[4]),
                    Double.parseDouble(parts[5]),
                    Double.parseDouble(parts[6]),
                    Integer.parseInt(parts[7]),
                    TransactionStatus.valueOf(parts[8]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private SQLException sql(String message, Exception cause) {
        return new SQLException(message, cause);
    }
}
