// com/example/taskmgr/model/UtilityPeriod.java
package com.example.taskmgr.model;

public class UtilityPeriod {
    public long id;
    public int periodYear;
    public int periodMonth; // 0-based (Calendar.MONTH)
    public String house;
    public String note;
    public boolean paid;
    public Long paidAt;
    public long createdAt;

    public UtilityPeriod() {
    }

    public UtilityPeriod(int year, int month, String house, String note) {
        this.periodYear = year;
        this.periodMonth = month;
        this.house = house;
        this.note = note;
        this.createdAt = System.currentTimeMillis();
    }
}
