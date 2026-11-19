package com.example.taskmgr.model;

public class SmokingEntry {

    public long id;
    public long smokedAt;
    public int count = 1;
    public String note;

    public SmokingEntry() {
    }

    public SmokingEntry(long smokedAt, int count) {
        this.smokedAt = smokedAt;
        this.count = count;
    }
}
