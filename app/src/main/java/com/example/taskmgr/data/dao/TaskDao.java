package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskDao {

    private static final long SOON_WINDOW_MS = 3L * 24 * 60 * 60 * 1000;

    private final DatabaseHelper dbHelper;

    public TaskDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(Task t) {
        return insert(dbHelper.getWritableDatabase(), t);
    }

    public long insert(SQLiteDatabase db, Task t) {
        ContentValues cv = new ContentValues();
        cv.put("tab_id", t.tabId);
        cv.put("title", t.title);
        if (t.description != null) cv.put("description", t.description);
        else cv.putNull("description");
        cv.put("created_at", t.createdAt);
        cv.put("priority", t.priority != null ? t.priority : "normal");
        cv.put("status", t.status != null ? t.status : "active");
        cv.put("due_type", t.dueType != null ? t.dueType : "none");
        putNullableLong(cv, "due_date", t.dueDate);
        putNullableLong(cv, "due_from", t.dueFrom);
        putNullableLong(cv, "due_to", t.dueTo);
        putNullableInt(cv, "due_km", t.dueKm);
        cv.put("repeat_rule", t.repeatRule != null ? t.repeatRule : "none");
        putNullableInt(cv, "repeat_interval_km", t.repeatIntervalKm);
        putNullableInt(cv, "repeat_interval_months", t.repeatIntervalMonths);
        return db.insert("tasks", null, cv);
    }

    public List<Task> getByTab(long tabId) {
        return query("tab_id = ? AND status = 'active'",
                new String[]{String.valueOf(tabId)},
                "created_at DESC");
    }

    public List<Task> getAllByTab(long tabId) {
        return query("tab_id = ?",
                new String[]{String.valueOf(tabId)},
                "created_at DESC");
    }

    /**
     * Количество активных задач по каждой вкладке указанного раздела.
     * Возвращает map: tab_id → count. Вкладки без активных задач в map не попадают.
     */
    public Map<Long, Integer> getActiveCountsBySection(long sectionId) {
        Map<Long, Integer> result = new HashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT tab_id, COUNT(*) FROM tasks " +
                            "WHERE status = 'active' " +
                            "AND tab_id IN (SELECT id FROM tabs WHERE section_id = ?) " +
                            "GROUP BY tab_id",
                    new String[]{String.valueOf(sectionId)});
            while (c.moveToNext()) {
                result.put(c.getLong(0), c.getInt(1));
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    /**
     * Вкладка «Дедлайн»: активные, приоритет не important, срок уже/в ближайшие 3 дня.
     * @param excludedTabIds вкладки, которые надо исключить (например, приватные при блокировке).
     *                       null или пусто — без исключения.
     */
    public List<Task> getDeadlineSoon(Collection<Long> excludedTabIds) {
        long now = System.currentTimeMillis();
        long soon = now + SOON_WINDOW_MS;

        StringBuilder where = new StringBuilder();
        where.append("status = 'active' ")
                .append("AND (priority IS NULL OR priority <> 'important') ")
                .append("AND ( ")
                .append("  (due_type = 'exact_date' AND due_date IS NOT NULL AND due_date <= ?) ")
                .append("  OR (due_type = 'range' AND ( ")
                .append("       (due_to IS NOT NULL AND due_to <= ?) ")
                .append("       OR (due_from IS NOT NULL AND due_from <= ?) ")
                .append("     )) ")
                .append("  OR (due_type = 'by_km' AND due_km IS NOT NULL) ")
                .append(")");

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(soon));
        args.add(String.valueOf(soon));
        args.add(String.valueOf(soon));

        appendNotIn(where, args, "tab_id", excludedTabIds);

        return query(where.toString(), args.toArray(new String[0]), "created_at DESC");
    }

    /**
     * Вкладка «Важные»: активные priority = important.
     * @param excludedTabIds см. getDeadlineSoon.
     */
    public List<Task> getImportant(Collection<Long> excludedTabIds) {
        StringBuilder where = new StringBuilder();
        where.append("status = 'active' AND priority = 'important'");
        List<String> args = new ArrayList<>();
        appendNotIn(where, args, "tab_id", excludedTabIds);
        return query(where.toString(), args.toArray(new String[0]), "created_at DESC");
    }

    public Task getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("tasks", null, "id = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            return c.moveToFirst() ? fromCursor(c) : null;
        } finally {
            if (c != null) c.close();
        }
    }

    public void updateStatus(long id, String status) {
        updateStatus(dbHelper.getWritableDatabase(), id, status);
    }

    public void updateStatus(SQLiteDatabase db, long id, String status) {
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        db.update("tasks", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public void update(Task t) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", t.title);
        if (t.description != null) cv.put("description", t.description);
        else cv.putNull("description");
        cv.put("priority", t.priority);
        cv.put("status", t.status);
        cv.put("due_type", t.dueType);
        putNullableLong(cv, "due_date", t.dueDate);
        putNullableLong(cv, "due_from", t.dueFrom);
        putNullableLong(cv, "due_to", t.dueTo);
        putNullableInt(cv, "due_km", t.dueKm);
        cv.put("repeat_rule", t.repeatRule);
        putNullableInt(cv, "repeat_interval_km", t.repeatIntervalKm);
        putNullableInt(cv, "repeat_interval_months", t.repeatIntervalMonths);
        db.update("tasks", cv, "id = ?", new String[]{String.valueOf(t.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("tasks", "id = ?", new String[]{String.valueOf(id)});
    }

    private void appendNotIn(StringBuilder where, List<String> args,
                             String column, Collection<Long> excluded) {
        if (excluded == null || excluded.isEmpty()) return;
        where.append(" AND ").append(column).append(" NOT IN (");
        int i = 0;
        for (Long id : excluded) {
            if (i > 0) where.append(",");
            where.append("?");
            args.add(String.valueOf(id));
            i++;
        }
        where.append(")");
    }

    private List<Task> query(String where, String[] args, String orderBy) {
        List<Task> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("tasks", null, where, args, null, null, orderBy);
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        } finally {
            if (c != null) c.close();
        }
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

    private void putNullableLong(ContentValues cv, String key, Long value) {
        if (value != null) cv.put(key, value);
        else cv.putNull(key);
    }

    private void putNullableInt(ContentValues cv, String key, Integer value) {
        if (value != null) cv.put(key, value);
        else cv.putNull(key);
    }
}