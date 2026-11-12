package com.example.taskmgr.model;

public class UtilityMeter {
    public long id;
    public String name;
    public String unit;
    public double rate;
    public boolean isActive = true;
    public int sortOrder;
    public long createdAt;

    public UtilityMeter() {
    }

    public UtilityMeter(String name, String unit, double rate) {
        this.name = name;
        this.unit = unit;
        this.rate = rate;
        this.createdAt = System.currentTimeMillis();
    }
}
