package com.example.taskmgr.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.DatabaseHelper;
import com.example.taskmgr.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskDao {
    private final DatabaseHelper dbHelper;

    public TaskDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public long insert(Task t) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("tab_id", t.tabId);
        cv.put("title", t.title);
        cv.put("description", t.description);
        cv.put("created_at", t.createdAt);
        cv.put("priority", t.priority);
        cv.put("status", t.status);
        cv.put("due_type", t.dueType);
        if (t.dueDate != null) cv.put("due_date", t.dueDate);
        if (t.dueFrom != null) cv.put("due_from", t.dueFrom);
        if (t.dueTo != null) cv.put("due_to", t.dueTo);
        if (t.dueKm != null) cv.put("due_km", t.dueKm);
        cv.put("repeat_rule", t.repeatRule);
        if (t.repeatIntervalKm != null) cv.put("repeat_interval_km", t.repeatIntervalKm);
        if (t.repeatIntervalMonths != null) cv.put("repeat_interval_months", t.repeatIntervalMonths);
        return db.insert("tasks", null, cv);
    }

    public List<Task> getByTab(long tabId) {
        return query("tab_id = ? AND status = 'active'",
                new String[]{String.valueOf(tabId)},
                "created_at DESC");
    }

    /** Для вкладки «Дедлайн» на главной: все активные, сортировка по сроку. */
    public List<Task> getActiveSortedByDue() {
        String order = "CASE due_type " +
                "WHEN 'exact_date' THEN due_date " +
                "WHEN 'range' THEN due_from " +
                "WHEN 'by_km' THEN due_km " +
                "WHEN 'after_last' THEN due_date " +
                "ELSE 9999999999 END ASC, created_at DESC";
        return query("status = 'active'", null, order);
    }

    /** Для вкладки «Важные» на главной: только приоритет important. */
    public List<Task> getActiveImportant() {
        return query("status = 'active' AND priority = 'important'", null, "created_at DESC");
    }

    public Task getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("tasks", null, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null);
        Task t = null;
        if (c.moveToFirst()) {
            t = fromCursor(c);
        }
        c.close();
        return t;
    }

    public void updateStatus(long id, String status) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        db.update("tasks", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("tasks", "id = ?", new String[]{String.valueOf(id)});
    }

    private List<Task> query(String where, String[] args, String orderBy) {
        List<Task> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("tasks", null, where, args, null, null, orderBy);
        while (c.moveToNext()) {
            result.add(fromCursor(c));
        }
        c.close();
        return result;
    }

    private Task fromCursor(Cursor c) {
        Task t = new Task();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.tabId = c.getLong(c.getColumnIndexOrThrow("tab_id"));
        t.title = c.getString(c.getColumnIndexOrThrow("title"));
        t.description = c.getString(c.getColumnIndexOrThrow("description"));
        t.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        t.priority = c.getString(c.getColumnIndexOrThrow("priority"));
        t.status = c.getString(c.getColumnIndexOrThrow("status"));
        t.dueType = c.getString(c.getColumnIndexOrThrow("due_type"));
        t.dueDate = getNullableLong(c, "due_date");
        t.dueFrom = getNullableLong(c, "due_from");
        t.dueTo = getNullableLong(c, "due_to");
        t.dueKm = getNullableInt(c, "due_km");
        t.repeatRule = c.getString(c.getColumnIndexOrThrow("repeat_rule"));
        t.repeatIntervalKm = getNullableInt(c, "repeat_interval_km");
        t.repeatIntervalMonths = getNullableInt(c, "repeat_interval_months");
        return t;
    }

    private Long getNullableLong(Cursor c, String column) {
        int idx = c.getColumnIndexOrThrow(column);
        return c.isNull(idx) ? null : c.getLong(idx);
    }

    private Integer getNullableInt(Cursor c, String column) {
        int idx = c.getColumnIndexOrThrow(column);
        return c.isNull(idx) ? null : c.getInt(idx);
    }
}