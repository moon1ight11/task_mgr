package com.example.taskmgr.model;

public class Section {
    public long id;
    public String name;
    public String icon;
    public int sortOrder;

    public Section() {}

    public Section(String name, String icon, int sortOrder) {
        this.name = name;
        this.icon = icon;
        this.sortOrder = sortOrder;
    }
}