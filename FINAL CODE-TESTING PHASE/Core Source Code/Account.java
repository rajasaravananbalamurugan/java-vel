package com.atm.model;

import com.atm.exception.DailyLimitExceededException;
import com.atm.exception.InsufficientFundsException;

public abstract class Account {
    private final String accountNumber;
    private final int customerId;
    protected double balance;
    private int version;
    private final double dailyLimit;
    private double withdrawnToday;

    public Account(String accountNumber, int customerId, double balance, int version, double dailyLimit) {
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.balance = balance;
        this.version = version;
        this.dailyLimit = dailyLimit;
        this.withdrawnToday = 0.0;
    }

    public String getAccountNumber() { return accountNumber; }
    public int getCustomerId() { return customerId; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public double getDailyLimit() { return dailyLimit; }
    public double getWithdrawnToday() { return withdrawnToday; }
    public void setWithdrawnToday(double withdrawnToday) { this.withdrawnToday = withdrawnToday; }

    public void incrementVersion() {
        this.version++;
    }

    public abstract void withdraw(double amount) throws InsufficientFundsException, DailyLimitExceededException;

    public void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        this.balance += amount;
    }

    protected void validateDailyLimit(double amount) throws DailyLimitExceededException {
        if (this.withdrawnToday + amount > this.dailyLimit) {
            throw new DailyLimitExceededException("Daily withdrawal limit of " + dailyLimit + " exceeded.");
        }
    }

    protected void recordWithdrawal(double amount) {
        this.withdrawnToday += amount;
    }
}