package com.example.taskmgr.model;

public class Tab {
    public long id;
    public long sectionId;
    public String name;
    public Integer currentKm; // null для не-авто
    public Integer year;      // null для не-авто
    public int sortOrder;

    public Tab() {}

    public Tab(long sectionId, String name) {
        this.sectionId = sectionId;
        this.name = name;
    }
}