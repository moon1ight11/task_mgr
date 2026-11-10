package com.example.taskmgr.model;

public class Section {
    public long id;
    public String name;
    public String icon;
    public boolean isPrivate = false;
    public int sortOrder;

    public Section() {
    }

    public Section(String name, String icon, int sortOrder) {
        this(name, icon, sortOrder, false);
    }

    public Section(String name, String icon, int sortOrder, boolean isPrivate) {
        this.name = name;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.isPrivate = isPrivate;
    }
}
