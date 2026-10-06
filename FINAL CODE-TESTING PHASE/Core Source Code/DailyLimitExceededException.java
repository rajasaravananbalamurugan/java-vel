package com.atm.exception;

public class DailyLimitExceededException extends Exception {
    public DailyLimitExceededException(String message) { super(message); }
}