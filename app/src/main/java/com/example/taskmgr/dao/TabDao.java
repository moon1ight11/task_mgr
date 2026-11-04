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
        if (t.year != null) cv.put("year", t.year);
        cv.put("sort_order", t.sortOrder);
        return db.insert("tabs", null, cv);
    }

    public List<Tab> getBySection(long sectionId) {
        List<Tab> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("tabs", null, "section_id = ?",
                new String[]{String.valueOf(sectionId)},
                null, null, "sort_order ASC");
        while (c.moveToNext()) {
            Tab t = new Tab();
            t.id = c.getLong(c.getColumnIndexOrThrow("id"));
            t.sectionId = c.getLong(c.getColumnIndexOrThrow("section_id"));
            t.name = c.getString(c.getColumnIndexOrThrow("name"));
            int kmIdx = c.getColumnIndexOrThrow("current_km");
            t.currentKm = c.isNull(kmIdx) ? null : c.getInt(kmIdx);
            int yearIdx = c.getColumnIndexOrThrow("year");
            t.year = c.isNull(yearIdx) ? null : c.getInt(yearIdx);
            t.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
            result.add(t);
        }
        c.close();
        return result;
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("tabs", "id = ?", new String[]{String.valueOf(id)});
    }
}