package com.example.taskmgr.model;

public class FinancePeriod {
    public long id;
    public long startAt;
    public long endAt;
    public String name;
    public double totalIncome;
    public String note;
    public long createdAt;

    public FinancePeriod() {}

    public FinancePeriod(long startAt, long endAt, String name, double totalIncome) {
        this.startAt = startAt;
        this.endAt = endAt;
        this.name = name;
        this.totalIncome = totalIncome;
        this.createdAt = System.currentTimeMillis();
    }
}
