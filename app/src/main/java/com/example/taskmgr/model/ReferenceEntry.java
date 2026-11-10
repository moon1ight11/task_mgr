package com.example.taskmgr.model;

public class ReferenceEntry {
    public long id;
    public long tabId;
    public String title;
    public String value;
    public String category;
    public String description;

    public ReferenceEntry() {
    }

    public ReferenceEntry(long tabId, String title, String value, String category) {
        this.tabId = tabId;
        this.title = title;
        this.value = value;
        this.category = category;
    }

    public ReferenceEntry(long tabId, String title, String value, String category, String description) {
        this(tabId, title, value, category);
        this.description = description;
    }
}
