package com.atm.model;

import com.atm.exception.DailyLimitExceededException;
import com.atm.exception.InsufficientFundsException;

public class CurrentAccount extends Account {
    private final double overdraftLimit;

    public CurrentAccount(String accountNumber, int customerId, double balance, int version, double dailyLimit, double overdraftLimit) {
        super(accountNumber, customerId, balance, version, dailyLimit);
        this.overdraftLimit = overdraftLimit;
    }

    public double getOverdraftLimit() { return overdraftLimit; }

    @Override
    public void withdraw(double amount) throws InsufficientFundsException, DailyLimitExceededException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        validateDailyLimit(amount);
        if (this.balance - amount < -overdraftLimit) {
            throw new InsufficientFundsException("Exceeds overdraft limit of " + overdraftLimit);
        }
        this.balance -= amount;
        recordWithdrawal(amount);
    }
}