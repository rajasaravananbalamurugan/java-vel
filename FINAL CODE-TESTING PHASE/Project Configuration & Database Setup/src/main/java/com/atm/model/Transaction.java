package com.atm.model;

import java.sql.Timestamp;

public class Transaction {
    private final String transactionId;
    private final String accountNumber;
    private final TransactionType type;
    private final double amount;
    private final Timestamp timestamp;
    private final double previousBalance;
    private final double resultingBalance;
    private final TransactionStatus status;

    public Transaction(String transactionId, String accountNumber, TransactionType type, double amount,
                       Timestamp timestamp, double previousBalance, double resultingBalance, TransactionStatus status) {
        this.transactionId = transactionId;
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
        this.previousBalance = previousBalance;
        this.resultingBalance = resultingBalance;
        this.status = status;
    }

    public String getTransactionId() { return transactionId; }
    public String getAccountNumber() { return accountNumber; }
    public TransactionType getType() { return type; }
    public double getAmount() { return amount; }
    public Timestamp getTimestamp() { return timestamp; }
    public double getPreviousBalance() { return previousBalance; }
    public double getResultingBalance() { return resultingBalance; }
    public TransactionStatus getStatus() { return status; }
}