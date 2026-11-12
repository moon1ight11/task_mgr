// com/example/taskmgr/model/FinanceTemplate.java
package com.example.taskmgr.model;

public class FinanceTemplate {
    public long id;
    public String category;
    public String title;
    public double defaultAmount;
    public boolean isActive = true;
    public int sortOrder;
    public long createdAt;

    public FinanceTemplate() {}

    public FinanceTemplate(String category, String title, double defaultAmount) {
        this.category = category;
        this.title = title;
        this.defaultAmount = defaultAmount;
        this.createdAt = System.currentTimeMillis();
    }
}
