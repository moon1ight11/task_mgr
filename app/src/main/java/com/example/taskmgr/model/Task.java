package com.example.taskmgr.model;

public class Task {
    public long id;
    public long tabId;
    public String title;
    public String description;
    public long createdAt;
    public String priority;   // important / normal / whatever
    public String status;     // active / done / deleted
    public String dueType;    // exact_date / range / by_km / after_last / none
    public Long dueDate;
    public Long dueFrom;
    public Long dueTo;
    public Integer dueKm;
    public String repeatRule; // none / by_km / by_months / by_km_or_months
    public Integer repeatIntervalKm;
    public Integer repeatIntervalMonths;

    public Task() {}

    public Task(long tabId, String title, String priority) {
        this.tabId = tabId;
        this.title = title;
        this.priority = priority;
        this.status = "active";
        this.dueType = "none";
        this.repeatRule = "none";
        this.createdAt = System.currentTimeMillis();
    }
}