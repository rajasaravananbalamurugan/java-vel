package com.atm.ui;

import com.atm.dao.OfflineTransactionDAO;
import com.atm.infrastructure.ATMConfig;
import com.atm.model.OfflineTransaction;
import com.atm.synchronization.SynchronizationManager;

import java.util.List;
import java.util.Scanner;

public class AdminConsoleUI {
    private final OfflineTransactionDAO offlineDAO = new OfflineTransactionDAO();

    public void showAdminMenu(Scanner scanner, SynchronizationManager syncManager) {
        while (true) {
            System.out.println("\n========================================");
            System.out.println("          ADMIN / DEMO CONTROLS         ");
            System.out.println("========================================");
            System.out.println("ATM ID          : " + ATMConfig.getAtmId());
            System.out.println("ATM Location    : " + ATMConfig.getAtmLocation());
            System.out.println("Central Server  : "
                    + ATMConfig.getCentralDbHost() + ":"
                    + ATMConfig.getCentralDbPort());
            System.out.println("----------------------------------------");
            System.out.println("1. Show Multi-ATM Configuration");
            System.out.println("2. Trigger Manual Synchronization");
            System.out.println("3. View Pending Offline Journal");
            System.out.println("4. Back to Main Menu");
            System.out.print("Choose an option: ");

            String input = scanner.nextLine();
            switch (input) {
                case "1" -> showConfiguration();
                case "2" -> syncManager.triggerManualSync();
                case "3" -> displayOfflineJournal();
                case "4" -> { return; }
                default -> System.out.println("Invalid selection.");
            }
        }
    }

    private void showConfiguration() {
        System.out.println("\n--- MULTI-ATM CONFIGURATION ---");
        System.out.println("ATM ID             : " + ATMConfig.getAtmId());
        System.out.println("Location           : " + ATMConfig.getAtmLocation());
        System.out.println("Central DB Host    : " + ATMConfig.getCentralDbHost());
        System.out.println("Central DB Port    : " + ATMConfig.getCentralDbPort());
        System.out.println("Central DB Name    : " + ATMConfig.getCentralDbName());
        System.out.println("Local LVDS Folder  : "
                + com.atm.infrastructure.LVDS.getInstance().getRootDirectory().toAbsolutePath());
    }

    private void displayOfflineJournal() {
        try {
            List<OfflineTransaction> pending =
                    offlineDAO.getPendingTransactions();

            System.out.println("\n--- PENDING OFFLINE JOURNAL ---");
            if (pending.isEmpty()) {
                System.out.println("No pending offline transactions.");
                return;
            }

            for (OfflineTransaction tx : pending) {
                System.out.printf(
                        "ID: %s | Acc: %s | Type: %s | Amount: %.2f | BaseVer: %d | Status: %s%n",
                        tx.getTransactionId(),
                        tx.getAccountNumber(),
                        tx.getType(),
                        tx.getAmount(),
                        tx.getBaseVersion(),
                        tx.getSyncStatus());
            }
        } catch (Exception e) {
            System.err.println("Error reading local journal: " + e.getMessage());
        }
    }
}
