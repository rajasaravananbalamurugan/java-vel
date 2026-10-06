package com.atm.model;

public enum TransactionStatus {
    SUCCESS,
    FAILED,
    PENDING_SYNC,
    SYNCHRONIZED,
    CONFLICT
}