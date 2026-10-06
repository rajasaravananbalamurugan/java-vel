package com.atm.service;

import com.atm.dao.OfflineTransactionDAO;
import com.atm.infrastructure.LVDS;
import com.atm.infrastructure.DatabaseConnection;
import com.atm.model.OfflineTransaction;
import com.atm.model.TransactionStatus;
import com.atm.synchronization.ReconciliationStrategy;

import java.sql.Connection;
import java.util.List;

public class SynchronizationService {
    private final OfflineTransactionDAO offlineDAO = new OfflineTransactionDAO();
    private final ReconciliationStrategy strategy = new ReconciliationStrategy();
    private final LVDS lvds = LVDS.getInstance();

    public synchronized void synchronize() {
        try {
            List<OfflineTransaction> pending = offlineDAO.getPendingTransactions();
            if (pending.isEmpty()) return;

            System.out.println(
                    "\n[SYNC ENGINE] Syncing " + pending.size()
                            + " offline transaction(s) from " + com.atm.infrastructure.ATMConfig.getAtmId() + "...");

            for (OfflineTransaction tx : pending) {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        strategy.reconcile(tx, conn);
                        conn.commit();

                        offlineDAO.updateSyncStatus(
                                tx.getTransactionId(), TransactionStatus.SYNCHRONIZED);

                        System.out.println(
                                "[SYNC ENGINE] Reconciled Tx ID: " + tx.getTransactionId());
                    } catch (Exception e) {
                        conn.rollback();

                        try {
                            offlineDAO.updateSyncStatus(
                                    tx.getTransactionId(), TransactionStatus.CONFLICT);
                        } catch (Exception statusError) {
                            System.err.println(
                                    "[SYNC ENGINE] Could not update local journal status: "
                                            + statusError.getMessage());
                        }

                        System.err.println(
                                "[SYNC ENGINE] Conflict/Error on Tx ID "
                                        + tx.getTransactionId() + ": " + e.getMessage());
                    }
                }
            }

            // After reconciliation, refresh the ATM's local snapshot from the
            // shared central server so future offline work starts from the
            // current central state.
            if (com.atm.infrastructure.NetworkManager.isOnline()) {
                try {
                    lvds.refreshFromCentral();
                } catch (Exception e) {
                    System.err.println(
                            "[LVDS] Central snapshot refresh failed after sync: "
                                    + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println(
                    "[SYNC ENGINE] Synchronization halted due to system failure: "
                            + e.getMessage());
        }
    }
}
