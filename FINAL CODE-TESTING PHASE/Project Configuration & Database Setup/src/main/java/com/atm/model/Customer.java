package com.atm.model;

public class Customer {
    private final int customerId;
    private final String name;
    private final String pin;

    public Customer(int customerId, String name, String pin) {
        this.customerId = customerId;
        this.name = name;
        this.pin = pin;
    }

    public int getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getPin() { return pin; }
}