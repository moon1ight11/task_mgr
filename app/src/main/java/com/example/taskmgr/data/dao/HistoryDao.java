package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.HistoryEntry;

import java.util.ArrayList;
import java.util.List;

public class HistoryDao {
    private final DatabaseHelper dbHelper;

    public HistoryDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(SQLiteDatabase db, HistoryEntry h) {
        ContentValues cv = new ContentValues();
        cv.put("task_id", h.taskId);
        cv.put("completed_at", h.completedAt);
        if (h.kmAtCompletion != null) cv.put("km_at_completion", h.kmAtCompletion);
        if (h.note != null) cv.put("note", h.note);
        return db.insert("history", null, cv);
    }

    public List<HistoryEntry> getByTask(long taskId) {
        List<HistoryEntry> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("history", null, "task_id = ?", new String[]{String.valueOf(taskId)}, null, null, "completed_at DESC")) {
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        }
        return result;
    }

    public List<HistoryEntry> getByTab(long tabId) {
        List<HistoryEntry> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT h.id, h.task_id, h.completed_at, h.km_at_completion, h.note " +
            "FROM history h " +
            "JOIN tasks t ON t.id = h.task_id " +
            "WHERE t.tab_id = ? " +
            "ORDER BY h.completed_at DESC";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(tabId)})) {
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        }
        return result;
    }

    public void deleteById(SQLiteDatabase db, long historyId) {
        db.delete("history", "id = ?", new String[]{String.valueOf(historyId)});
    }

    private HistoryEntry fromCursor(Cursor c) {
        HistoryEntry h = new HistoryEntry();
        h.id = c.getLong(c.getColumnIndexOrThrow("id"));
        h.taskId = c.getLong(c.getColumnIndexOrThrow("task_id"));
        h.completedAt = c.getLong(c.getColumnIndexOrThrow("completed_at"));
        int kmIdx = c.getColumnIndexOrThrow("km_at_completion");
        h.kmAtCompletion = c.isNull(kmIdx) ? null : c.getInt(kmIdx);
        h.note = c.getString(c.getColumnIndexOrThrow("note"));
        return h;
    }
}
