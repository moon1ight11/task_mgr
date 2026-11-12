package com.example.taskmgr.model;

public class Subscription {
    public long id;
    public String name;
    public double amount;
    public int chargeDay;       // 1..31
    public long nextChargeAt;   // timestamp
    public String description;
    public boolean isActive = true;
    public long createdAt;

    public Subscription() {
    }

    public Subscription(String name, double amount, int chargeDay,
                        long nextChargeAt, String description, boolean isActive) {
        this.name = name;
        this.amount = amount;
        this.chargeDay = chargeDay;
        this.nextChargeAt = nextChargeAt;
        this.description = description;
        this.isActive = isActive;
        this.createdAt = System.currentTimeMillis();
    }
}
