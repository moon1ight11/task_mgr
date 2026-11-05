package com.example.taskmgr.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.DatabaseHelper;
import com.example.taskmgr.model.HistoryEntry;

import java.util.ArrayList;
import java.util.List;

public class HistoryDao {
    private final DatabaseHelper dbHelper;

    public HistoryDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public long insert(HistoryEntry h) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
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
        Cursor c = null;
        try {
            c = db.query("history", null, "task_id = ?",
                    new String[]{String.valueOf(taskId)},
                    null, null, "completed_at DESC");
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public HistoryEntry getLastByTask(long taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("history", null, "task_id = ?",
                    new String[]{String.valueOf(taskId)},
                    null, null, "completed_at DESC", "1");
            return c.moveToFirst() ? fromCursor(c) : null;
        } finally {
            if (c != null) c.close();
        }
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