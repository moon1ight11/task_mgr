package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.QuickNote;

import java.util.ArrayList;
import java.util.List;

public class QuickNoteDao {
    private final DatabaseHelper dbHelper;

    public QuickNoteDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(String body) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("body", body);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        return db.insert("quick_notes", null, cv);
    }

    public void update(long id, String body) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("body", body);
        cv.put("updated_at", System.currentTimeMillis());
        db.update("quick_notes", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("quick_notes", "id = ?", new String[]{String.valueOf(id)});
    }

    public QuickNote getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("quick_notes", null, "id = ?",
            new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<QuickNote> getAll() {
        List<QuickNote> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("quick_notes", null, null, null,
            null, null, "created_at DESC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    private QuickNote fromCursor(Cursor c) {
        QuickNote n = new QuickNote();
        n.id = c.getLong(c.getColumnIndexOrThrow("id"));
        n.body = c.getString(c.getColumnIndexOrThrow("body"));
        n.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        n.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return n;
    }
}
