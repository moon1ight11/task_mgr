package com.example.taskmgr.model;

public class FinanceExpense {
    public long id;
    public long periodId;
    public String category;
    public String title;
    public double amount;
    public long createdAt;
    public String sourceType = "manual"; // manual | subscription | subscription_bundle | shopping
    public Long sourceRefId;
    public String sourceRefs; // NEW: CSV id, например "12,15,18" — для subscription_bundle
}
