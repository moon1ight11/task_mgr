package com.example.taskmgr.model;

public class QuickNote {
    public long id;
    public String body;
    public long createdAt;
    public long updatedAt;

    public QuickNote() {
    }

    public QuickNote(String body) {
        this.body = body;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }
}
