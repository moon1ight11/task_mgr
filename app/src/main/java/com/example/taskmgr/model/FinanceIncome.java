package com.example.taskmgr.model;

public class FinanceIncome {
    public long id;
    public String name;
    public double amount;
    public int dayOfMonth; // 1..31
    public boolean isActive = true;
    public int sortOrder;
    public long createdAt;

    public FinanceIncome() {}

    public FinanceIncome(String name, double amount, int dayOfMonth) {
        this.name = name;
        this.amount = amount;
        this.dayOfMonth = dayOfMonth;
        this.createdAt = System.currentTimeMillis();
    }
}
