package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.UtilityMeter;

import java.util.ArrayList;
import java.util.List;

public class UtilityMeterDao {
    private final DatabaseHelper dbHelper;

    public UtilityMeterDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(UtilityMeter m) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", m.name);
        cv.put("unit", m.unit);
        cv.put("rate", m.rate);
        cv.put("is_active", m.isActive ? 1 : 0);
        cv.put("sort_order", m.sortOrder);
        cv.put("created_at", m.createdAt);
        return db.insert("utility_meters", null, cv);
    }

    public void update(UtilityMeter m) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", m.name);
        cv.put("unit", m.unit);
        cv.put("rate", m.rate);
        cv.put("is_active", m.isActive ? 1 : 0);
        cv.put("sort_order", m.sortOrder);
        db.update("utility_meters", cv, "id = ?", new String[]{String.valueOf(m.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Каскадно удалит readings, но periods останутся (могут стать пустыми).
        db.delete("utility_meters", "id = ?", new String[]{String.valueOf(id)});
    }

    public void setActive(long id, boolean active) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_active", active ? 1 : 0);
        db.update("utility_meters", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public List<UtilityMeter> getAll(boolean activeOnly) {
        List<UtilityMeter> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String where = activeOnly ? "is_active = 1" : null;
        try (Cursor c = db.query("utility_meters", null, where, null,
            null, null, "sort_order ASC, name COLLATE NOCASE ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public UtilityMeter getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("utility_meters", null, "id = ?",
            new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    private UtilityMeter fromCursor(Cursor c) {
        UtilityMeter m = new UtilityMeter();
        m.id = c.getLong(c.getColumnIndexOrThrow("id"));
        m.name = c.getString(c.getColumnIndexOrThrow("name"));
        m.unit = c.getString(c.getColumnIndexOrThrow("unit"));
        m.rate = c.getDouble(c.getColumnIndexOrThrow("rate"));
        m.isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1;
        m.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
        m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return m;
    }
}
