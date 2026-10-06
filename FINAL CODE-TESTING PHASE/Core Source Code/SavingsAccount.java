package com.atm.model;

import com.atm.exception.DailyLimitExceededException;
import com.atm.exception.InsufficientFundsException;

public class SavingsAccount extends Account {
    private final double minimumBalance;

    public SavingsAccount(String accountNumber, int customerId, double balance, int version, double dailyLimit, double minimumBalance) {
        super(accountNumber, customerId, balance, version, dailyLimit);
        this.minimumBalance = minimumBalance;
    }

    public double getMinimumBalance() { return minimumBalance; }

    @Override
    public void withdraw(double amount) throws InsufficientFundsException, DailyLimitExceededException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        validateDailyLimit(amount);
        if (this.balance - amount < minimumBalance) {
            throw new InsufficientFundsException("Cannot breach minimum required balance of " + minimumBalance);
        }
        this.balance -= amount;
        recordWithdrawal(amount);
    }
}