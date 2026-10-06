package com.atm.model;

public class ATM {
    private final String atmId;
    private final String location;

    public ATM(String atmId, String location) {
        this.atmId = atmId;
        this.location = location;
    }

    public String getAtmId() { return atmId; }
    public String getLocation() { return location; }
}