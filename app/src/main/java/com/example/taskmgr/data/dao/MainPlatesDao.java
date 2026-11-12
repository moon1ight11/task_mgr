package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.data.MainPlatesCatalog;
import com.example.taskmgr.model.MainPlate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainPlatesDao {

    private final DatabaseHelper dbHelper;

    public MainPlatesDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /** Возвращает ВСЕ плитки каталога, объединённые с сохранёнными настройками. */
    public List<MainPlate> getAll() {
        Map<String, int[]> settings = readSettings(); // key -> [visible, sortOrder]
        List<MainPlate> result = new ArrayList<>();
        for (MainPlate tpl : MainPlatesCatalog.all()) {
            MainPlate p = tpl; // шаблон уже свежий
            int[] s = settings.get(p.key);
            if (s != null) {
                p.isVisible = s[0] != 0;
                p.sortOrder = s[1];
            } else {
                p.isVisible = true;
                p.sortOrder = Integer.MAX_VALUE;
            }
            result.add(p);
        }
        result.sort((a, b) -> Integer.compare(a.sortOrder, b.sortOrder));
        return result;
    }

    private Map<String, int[]> readSettings() {
        Map<String, int[]> map = new HashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("main_plates",
            new String[]{"plate_key", "is_visible", "sort_order"},
            null, null, null, null, null)) {
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

    /** Меняет sort_order у двух ключей местами. */
    public void swap(String keyA, String keyB) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Integer sa = readSortOrder(db, keyA);
        Integer sb = readSortOrder(db, keyB);
        if (sa == null && sb == null) return;
        if (sa == null) sa = nextSortOrder(db);
        if (sb == null) sb = nextSortOrder(db);
        updateSortOrder(db, keyA, sb);
        updateSortOrder(db, keyB, sa);
    }

    private Integer readSortOrder(SQLiteDatabase db, String key) {
        try (Cursor c = db.query("main_plates", new String[]{"sort_order"},
            "plate_key = ?", new String[]{key}, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : null;
        }
    }

    private void updateSortOrder(SQLiteDatabase db, String key, int order) {
        ContentValues cv = new ContentValues();
        cv.put("sort_order", order);
        int updated = db.update("main_plates", cv, "plate_key = ?", new String[]{key});
        if (updated == 0) insertRow(db, key, true, order);
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
