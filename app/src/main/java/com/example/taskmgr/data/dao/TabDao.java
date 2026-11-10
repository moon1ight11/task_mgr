package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.Tab;

import java.util.ArrayList;
import java.util.List;

public class TabDao {
    private final DatabaseHelper dbHelper;

    public TabDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
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
        cv.put("show_tasks", t.showTasks ? 1 : 0);
        cv.put("show_shopping", t.showShopping ? 1 : 0);
        cv.put("show_reference", t.showReference ? 1 : 0);
        return db.insert("tabs", null, cv);
    }

    public List<Tab> getBySection(long sectionId) {
        List<Tab> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("tabs", null, "section_id = ?",
            new String[]{String.valueOf(sectionId)},
            null, null, "sort_order ASC")) {
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        }
        return result;
    }

    public List<Tab> getAll() {
        List<Tab> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("tabs", null, null, null, null, null, "sort_order ASC")) {
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        }
        return result;
    }

    public List<Tab> getByPrivateSections() {
        List<Tab> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT t.* FROM tabs t " +
            "JOIN sections s ON s.id = t.section_id " +
            "WHERE s.is_private = 1";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        }
        return result;
    }

    public Tab getById(long id) {
        return getById(dbHelper.getReadableDatabase(), id);
    }

    public Tab getById(SQLiteDatabase db, long id) {
        try (Cursor c = db.query("tabs", null, "id = ?",
            new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public boolean isAutoTab(long tabId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT s.name FROM tabs t " +
                "JOIN sections s ON s.id = t.section_id " +
                "WHERE t.id = ?",
            new String[]{String.valueOf(tabId)})) {
            return c.moveToFirst() && SectionDao.AUTO_SECTION_NAME.equals(c.getString(0));
        }
    }

    public void update(Tab t) {
        update(dbHelper.getWritableDatabase(), t);
    }

    public void update(SQLiteDatabase db, Tab t) {
        ContentValues cv = new ContentValues();
        cv.put("name", t.name);
        if (t.currentKm != null) cv.put("current_km", t.currentKm);
        else cv.putNull("current_km");
        if (t.year != null) cv.put("year", t.year);
        else cv.putNull("year");
        cv.put("sort_order", t.sortOrder);
        cv.put("show_tasks", t.showTasks ? 1 : 0);
        cv.put("show_shopping", t.showShopping ? 1 : 0);
        cv.put("show_reference", t.showReference ? 1 : 0);
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
        t.showTasks = readBool(c, "show_tasks");
        t.showShopping = readBool(c, "show_shopping");
        t.showReference = readBool(c, "show_reference");
        return t;
    }

    private boolean readBool(Cursor c, String column) {
        int idx = c.getColumnIndex(column);
        if (idx == -1 || c.isNull(idx)) return true;
        return c.getInt(idx) != 0;
    }
}
