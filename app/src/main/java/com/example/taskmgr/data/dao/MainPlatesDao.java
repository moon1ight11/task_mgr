package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.data.MainPlatesCatalog;
import com.example.taskmgr.model.MainPlate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainPlatesDao {
    private final DatabaseHelper dbHelper;

    public MainPlatesDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public List<MainPlate> getAll() {
        Map<String, int[]> settings = readSettings();
        List<MainPlate> result = new ArrayList<>();
        for (MainPlate tpl : MainPlatesCatalog.all()) {
            int[] s = settings.get(tpl.key);
            if (s != null) {
                tpl.isVisible = s[0] != 0;
                tpl.sortOrder = s[1];
            } else {
                tpl.isVisible = true;
                tpl.sortOrder = Integer.MAX_VALUE;
            }
            result.add(tpl);
        }
        result.sort(Comparator.comparingInt(a -> a.sortOrder));
        return result;
    }

    private Map<String, int[]> readSettings() {
        Map<String, int[]> map = new HashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("main_plates", new String[]{"plate_key", "is_visible", "sort_order"}, null, null, null, null, null)) {
            while (c.moveToNext()) {
                map.put(c.getString(0), new int[]{c.getInt(1), c.getInt(2)});
            }
        }
        return map;
    }

    public void setVisible(String key, boolean visible) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_visible", visible ? 1 : 0);
        int updated = db.update("main_plates", cv, "plate_key = ?", new String[]{key});
        if (updated == 0) insertRow(db, key, visible, nextSortOrder(db));
    }

    public void reorder(List<MainPlate> ordered) {
        if (ordered == null || ordered.isEmpty()) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ordered.size(); i++) {
                MainPlate p = ordered.get(i);
                ContentValues cv = new ContentValues();
                cv.put("sort_order", i);
                int updated = db.update("main_plates", cv, "plate_key = ?", new String[]{p.key});
                if (updated == 0) {
                    ContentValues ins = new ContentValues();
                    ins.put("plate_key", p.key);
                    ins.put("is_visible", p.isVisible ? 1 : 0);
                    ins.put("sort_order", i);
                    db.insertWithOnConflict("main_plates", null, ins, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private int nextSortOrder(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM main_plates", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private void insertRow(SQLiteDatabase db, String key, boolean visible, int order) {
        ContentValues cv = new ContentValues();
        cv.put("plate_key", key);
        cv.put("is_visible", visible ? 1 : 0);
        cv.put("sort_order", order);
        db.insertWithOnConflict("main_plates", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
