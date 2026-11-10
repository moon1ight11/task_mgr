package com.example.taskmgr.model;

public class ShoppingItem {
    public long id;
    public long tabId;
    public String title;
    public Double price;
    public boolean isBought;
    public boolean isSelected;
    public long createdAt;
    public String category;

    public ShoppingItem() {
    }

    public ShoppingItem(long tabId, String title, Double price) {
        this.tabId = tabId;
        this.title = title;
        this.price = price;
        this.createdAt = System.currentTimeMillis();
    }

    public ShoppingItem(long tabId, String title, Double price, String category) {
        this(tabId, title, price);
        this.category = category;
    }
}
