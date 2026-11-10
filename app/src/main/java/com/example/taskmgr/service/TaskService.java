package com.example.taskmgr.service;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.data.dao.HistoryDao;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.data.dao.TaskDao;
import com.example.taskmgr.model.HistoryEntry;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;

public class TaskService {

    private final DatabaseHelper dbHelper;
    private final TaskDao taskDao;
    private final HistoryDao historyDao;
    private final TabDao tabDao;

    public TaskService(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
        this.taskDao = new TaskDao(context);
        this.historyDao = new HistoryDao(context);
        this.tabDao = new TabDao(context);
    }

    public void completeTask(Task task, Integer kmAtCompletion, String note) {
        long now = System.currentTimeMillis();
        dbHelper.runInTransaction(db -> {
            HistoryEntry h = new HistoryEntry();
            h.taskId = task.id;
            h.completedAt = now;
            h.kmAtCompletion = kmAtCompletion;
            h.note = note;
            historyDao.insert(db, h);

            taskDao.updateStatus(db, task.id, "done");

            if (kmAtCompletion != null) {
                updateTabKmIfNeeded(db, task.tabId, kmAtCompletion);
            }

            if (task.repeatRule != null && !task.repeatRule.equals("none")) {
                createNextTask(db, task, now, kmAtCompletion);
            }
        });
    }

    private void updateTabKmIfNeeded(SQLiteDatabase db, long tabId, int km) {
        Tab tab = tabDao.getById(db, tabId);
        if (tab == null) return;
        if (tab.currentKm == null || km > tab.currentKm) {
            tab.currentKm = km;
            tabDao.update(db, tab);
        }
    }

    private void createNextTask(SQLiteDatabase db, Task done, long completedAt, Integer kmAtCompletion) {
        Task next = new Task(done.tabId, done.title, done.priority);
        next.description = done.description;
        next.repeatRule = done.repeatRule;
        next.repeatIntervalKm = done.repeatIntervalKm;
        next.repeatIntervalMonths = done.repeatIntervalMonths;
        next.status = "active";

        switch (done.repeatRule) {
            case "by_km":
                if (kmAtCompletion != null && done.repeatIntervalKm != null) {
                    next.dueType = "by_km";
                    next.dueKm = kmAtCompletion + done.repeatIntervalKm;
                } else {
                    next.dueType = "none";
                }
                break;

            case "by_months":
                if (done.repeatIntervalMonths != null) {
                    next.dueType = "exact_date";
                    next.dueDate = addMonths(completedAt, done.repeatIntervalMonths);
                } else {
                    next.dueType = "none";
                }
                break;

            case "by_km_or_months": {
                Long nextDate = done.repeatIntervalMonths != null
                    ? addMonths(completedAt, done.repeatIntervalMonths) : null;
                Integer nextKm = (kmAtCompletion != null && done.repeatIntervalKm != null)
                    ? kmAtCompletion + done.repeatIntervalKm : null;

                if (nextKm != null) {
                    next.dueType = "by_km";
                    next.dueKm = nextKm;
                    next.dueDate = nextDate;
                } else if (nextDate != null) {
                    next.dueType = "exact_date";
                    next.dueDate = nextDate;
                } else {
                    next.dueType = "none";
                }
                break;
            }

            default:
                next.dueType = "none";
        }

        taskDao.insert(db, next);
    }

    private long addMonths(long millis, int months) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.add(java.util.Calendar.MONTH, months);
        return cal.getTimeInMillis();
    }

    public void revertCompletedTask(long taskId, long historyId) {
        dbHelper.runInTransaction(db -> {
            historyDao.deleteById(db, historyId);
            taskDao.updateStatus(db, taskId, "active");
        });
    }
}
