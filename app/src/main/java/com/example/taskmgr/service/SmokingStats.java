package com.example.taskmgr.service;

public class SmokingStats {

    public int entriesCount;
    public int totalSmoked;
    public int todaySmoked;
    public int maxPerDay;
    public int avgPerDay;
    public int daysWithout;
    public Long firstSmokedAt;
    public Long lastSmokedAt;

    public boolean hasData() {
        return entriesCount > 0;
    }
}
