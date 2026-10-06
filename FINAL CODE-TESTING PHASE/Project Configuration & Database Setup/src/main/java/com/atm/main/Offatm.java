package com.atm.main;

import com.atm.infrastructure.ATMConfig;
import com.atm.infrastructure.LVDS;
import com.atm.infrastructure.NetworkManager;
import com.atm.synchronization.SynchronizationManager;
import com.atm.ui.ATMConsoleUI;
import com.atm.ui.AdminConsoleUI;

import java.util.Scanner;

public class Offatm {
    public static void main(String[] args) {
        ATMConfig.ensureConfigFile();

        System.out.println("========================================");
        System.out.println("  HYBRID ATM SIMULATION NETWORK         ");
        System.out.println("========================================");
        System.out.println("ATM ID          : " + ATMConfig.getAtmId());
        System.out.println("Central Server  : "
                + ATMConfig.getCentralDbHost() + ":"
                + ATMConfig.getCentralDbPort());
        System.out.println("Local LVDS      : "
                + LVDS.getInstance().getRootDirectory().toAbsolutePath());

        SynchronizationManager syncManager =
                new SynchronizationManager();

        // Pull the shared central snapshot once when this ATM starts online.
        syncManager.initializeLocalSnapshot();
        syncManager.startBackgroundTasks();

        ATMConsoleUI atmUI = new ATMConsoleUI();
        AdminConsoleUI adminUI = new AdminConsoleUI();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n========================================");
            System.out.println("              MAIN MENU                 ");
            System.out.println("========================================");
            System.out.println("1. Login (ATM Operations)");
            System.out.println("2. Admin / Demonstration Menu");
            System.out.println("3. Exit System");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine();
            switch (choice) {
                case "1" -> atmUI.start(scanner);
                case "2" -> adminUI.showAdminMenu(scanner, syncManager);
                case "3" -> {
                    System.out.println("Shutting down ATM Infrastructure...");
                    syncManager.shutdown();
                    System.out.println("System terminated cleanly.");
                    return;
                }
                default -> System.out.println("Invalid selection.");
            }
        }
    }
}
