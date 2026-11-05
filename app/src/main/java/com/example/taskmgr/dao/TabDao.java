package com.example.taskmgr.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.DatabaseHelper;
import com.example.taskmgr.model.Tab;

import java.util.ArrayList;
import java.util.List;

public class TabDao {
    private final DatabaseHelper dbHelper;

    public TabDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public long insert(Tab t) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("section_id", t.sectionId);
        cv.put("name", t.name);
        if (t.currentKm != null) cv.put("current_km", t.currentKm);
        else cv.putNull("current_km");
        if (t.year != null) cv.put("year", t.year);
        else cv.putNull("year");
        cv.put("sort_order", t.sortOrder);
        return db.insert("tabs", null, cv);
    }

    public List<Tab> getBySection(long sectionId) {
        List<Tab> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("tabs", null, "section_id = ?",
                    new String[]{String.valueOf(sectionId)},
                    null, null, "sort_order ASC");
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public List<Tab> getAll() {
        List<Tab> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("tabs", null, null, null, null, null, "sort_order ASC");
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    /** Все вкладки, принадлежащие приватным разделам. Используется для исключения на главной. */
    public List<Tab> getByPrivateSections() {
        List<Tab> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            String sql = "SELECT t.* FROM tabs t " +
                    "JOIN sections s ON s.id = t.section_id " +
                    "WHERE s.is_private = 1";
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public Tab getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("tabs", null, "id = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            return c.moveToFirst() ? fromCursor(c) : null;
        } finally {
            if (c != null) c.close();
        }
    }

    public void update(Tab t) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", t.name);
        if (t.currentKm != null) cv.put("current_km", t.currentKm);
        else cv.putNull("current_km");
        if (t.year != null) cv.put("year", t.year);
        else cv.putNull("year");
        cv.put("sort_order", t.sortOrder);
        db.update("tabs", cv, "id = ?", new String[]{String.valueOf(t.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("tabs", "id = ?", new String[]{String.valueOf(id)});
    }

    private Tab fromCursor(Cursor c) {
        Tab t = new Tab();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.sectionId = c.getLong(c.getColumnIndexOrThrow("section_id"));
        t.name = c.getString(c.getColumnIndexOrThrow("name"));
        int kmIdx = c.getColumnIndexOrThrow("current_km");
        t.currentKm = c.isNull(kmIdx) ? null : c.getInt(kmIdx);
        int yearIdx = c.getColumnIndexOrThrow("year");
        t.year = c.isNull(yearIdx) ? null : c.getInt(yearIdx);
        t.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
        return t;
    }
}