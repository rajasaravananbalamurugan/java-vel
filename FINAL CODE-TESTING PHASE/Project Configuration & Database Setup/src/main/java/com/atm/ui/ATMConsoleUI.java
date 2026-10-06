package com.atm.ui;

import com.atm.exception.AccountNotFoundException;
import com.atm.exception.DailyLimitExceededException;
import com.atm.exception.InsufficientFundsException;
import com.atm.exception.InvalidPINException;
import com.atm.infrastructure.ATMConfig;
import com.atm.model.Account;
import com.atm.model.Transaction;
import com.atm.service.AccountService;
import com.atm.service.AuthenticationService;
import com.atm.service.TransactionService;

import java.util.List;
import java.util.Scanner;

public class ATMConsoleUI {
    private final AuthenticationService authService = new AuthenticationService();
    private final TransactionService transactionService = new TransactionService();
    private final AccountService accountService = new AccountService();

    public void start(Scanner scanner) {
        System.out.print("Enter Account Number: ");
        String accountNumber = scanner.nextLine();
        System.out.print("Enter PIN: ");
        String pin = scanner.nextLine();

        try {
            Account account = authService.authenticate(accountNumber, pin);
            System.out.println("Authentication Successful! Welcome.");
            showSessionMenu(scanner, account);
        } catch (AccountNotFoundException | InvalidPINException e) {
            System.err.println("Authentication Failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("System Error during authentication: " + e.getMessage());
        }
    }

    private void showSessionMenu(Scanner scanner, Account account) {
        while (true) {
            System.out.println("\n========================================");
            System.out.println("               ATM MENU                 ");
            System.out.println("========================================");
            System.out.println("1. Balance Enquiry");
            System.out.println("2. Withdraw");
            System.out.println("3. Deposit");
            System.out.println("4. Transfer");
            System.out.println("5. Mini Statement");
            System.out.println("6. Account Details");
            System.out.println("7. Logout");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine();
            switch (choice) {
                case "1" -> {
                    if (authorizeOperation(scanner, account, "balance enquiry")) {
                        try {
                            Account current = transactionService.getCurrentAccount(account);
                            if (current == null) {
                                System.err.println("Account is unavailable.");
                            } else {
                                System.out.printf("Current Balance: $%.2f\n", current.getBalance());
                            }
                        } catch (Exception e) {
                            System.err.println("Balance enquiry failed: " + e.getMessage());
                        }
                    }
                }
                case "2" -> {
                    if (authorizeOperation(scanner, account, "withdrawal")) {
                        handleWithdrawal(scanner, account);
                    }
                }
                case "3" -> {
                    if (authorizeOperation(scanner, account, "deposit")) {
                        handleDeposit(scanner, account);
                    }
                }
                case "4" -> {
                    if (authorizeOperation(scanner, account, "transfer")) {
                        handleTransfer(scanner, account);
                    }
                }
                case "5" -> {
                    if (authorizeOperation(scanner, account, "mini statement")) {
                        handleMiniStatement(account);
                    }
                }
                case "6" -> {
                    if (authorizeOperation(scanner, account, "account details")) {
                        displayAccountDetails(account);
                    }
                }
                case "7" -> {
                    System.out.println("Logged out successfully.");
                    return;
                }
                default -> System.out.println("Invalid selection.");
            }
        }
    }

    /**
     * Requires a fresh PIN verification for every customer operation.
     */
    private boolean authorizeOperation(Scanner scanner, Account account, String operation) {
        System.out.print("Enter PIN to authorize " + operation + ": ");
        String pin = scanner.nextLine();
        try {
            authService.verifyOperationPIN(account, pin);
            System.out.println("PIN Verified Successfully.");
            return true;
        } catch (InvalidPINException e) {
            System.out.println("Transaction Denied!");
            System.out.println(e.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("Authorization Error: " + e.getMessage());
            return false;
        }
    }

    private void handleWithdrawal(Scanner scanner, Account account) {
        System.out.print("Enter Withdrawal Amount: ");
        try {
            double amount = Double.parseDouble(scanner.nextLine());
            transactionService.executeWithdrawal(account, amount);
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format.");
        } catch (InsufficientFundsException | DailyLimitExceededException e) {
            System.err.println("Transaction Rejected: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Transaction Error: " + e.getMessage());
        }
    }

    private void handleDeposit(Scanner scanner, Account account) {
        System.out.print("Enter Deposit Amount: ");
        try {
            double amount = Double.parseDouble(scanner.nextLine());
            transactionService.executeDeposit(account, amount);
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format.");
        } catch (Exception e) {
            System.err.println("Deposit Error: " + e.getMessage());
        }
    }

    private void handleTransfer(Scanner scanner, Account account) {
        System.out.print("Enter Destination Account Number: ");
        String destAcc = scanner.nextLine();
        System.out.print("Enter Transfer Amount: ");
        try {
            double amount = Double.parseDouble(scanner.nextLine());
            transactionService.executeTransfer(account, destAcc, amount);
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format.");
        } catch (Exception e) {
            System.err.println("Transfer Error: " + e.getMessage());
        }
    }

    private void handleMiniStatement(Account account) {
        try {
            List<Transaction> list =
                    transactionService.getMiniStatement(account.getAccountNumber());

            System.out.println("\n--- MINI STATEMENT (Recent 10) ---");
            if (list.isEmpty()) {
                System.out.println("No recorded transactions.");
                return;
            }

            for (Transaction tx : list) {
                System.out.printf("%s | %s | $%.2f | Status: %s | %s\n",
                        tx.getTimestamp(), tx.getType(), tx.getAmount(),
                        tx.getStatus(), tx.getTransactionId());
            }
        } catch (Exception e) {
            System.err.println("Mini statement unavailable: " + e.getMessage());
        }
    }

    private void displayAccountDetails(Account account) {
        try {
            Account current = transactionService.getCurrentAccount(account);
            if (current == null) {
                System.err.println("Account unavailable.");
                return;
            }

            System.out.println("\n--- ACCOUNT DETAILS ---");
            System.out.println("ATM ID: " + ATMConfig.getAtmId());
            System.out.println("Account Number: " + current.getAccountNumber());
            System.out.println("Account Type: " + current.getClass().getSimpleName());
            System.out.println("Balance: $" + current.getBalance());
            System.out.println("Version Number: " + current.getVersion());
            System.out.println("Daily Limit: $" + current.getDailyLimit());
        } catch (Exception e) {
            System.err.println("Account details unavailable: " + e.getMessage());
        }
    }
}
