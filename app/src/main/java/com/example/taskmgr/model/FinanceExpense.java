package com.example.taskmgr.model;

public class FinanceExpense {
    public long id;
    public long periodId;
    public String category;
    public String title;
    public double amount;
    public long createdAt;
    public String sourceType = "manual"; // manual | subscription | shopping
    public Long sourceRefId;
}
