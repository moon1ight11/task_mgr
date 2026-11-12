package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.UtilityPeriod;

import java.util.ArrayList;
import java.util.List;

public class UtilityPeriodDao {
    private final DatabaseHelper dbHelper;

    public UtilityPeriodDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(UtilityPeriod p) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("period_year", p.periodYear);
        cv.put("period_month", p.periodMonth);
        cv.put("house", p.house);
        if (p.note != null) cv.put("note", p.note);
        else cv.putNull("note");
        cv.put("paid", p.paid ? 1 : 0);
        if (p.paidAt != null) cv.put("paid_at", p.paidAt);
        else cv.putNull("paid_at");
        cv.put("created_at", p.createdAt);
        return db.insert("utility_periods", null, cv);
    }

    public void update(UtilityPeriod p) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("house", p.house);
        if (p.note != null) cv.put("note", p.note);
        else cv.putNull("note");
        cv.put("paid", p.paid ? 1 : 0);
        if (p.paidAt != null) cv.put("paid_at", p.paidAt);
        else cv.putNull("paid_at");
        db.update("utility_periods", cv, "id = ?", new String[]{String.valueOf(p.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("utility_periods", "id = ?", new String[]{String.valueOf(id)});
    }

    public void setPaid(long id, boolean paid) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("paid", paid ? 1 : 0);
        if (paid) cv.put("paid_at", System.currentTimeMillis());
        else cv.putNull("paid_at");
        db.update("utility_periods", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public UtilityPeriod find(int year, int month, String house) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("utility_periods", null,
            "period_year = ? AND period_month = ? AND house = ?",
            new String[]{String.valueOf(year), String.valueOf(month), house},
            null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public UtilityPeriod getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("utility_periods", null, "id = ?",
            new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<String> getHouses() {
        List<String> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT DISTINCT house FROM utility_periods " +
                "ORDER BY house COLLATE NOCASE ASC", null)) {
            while (c.moveToNext()) result.add(c.getString(0));
        }
        return result;
    }

    public String getLastHouse() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT house FROM utility_periods ORDER BY created_at DESC LIMIT 1", null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    private UtilityPeriod fromCursor(Cursor c) {
        UtilityPeriod p = new UtilityPeriod();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.periodYear = c.getInt(c.getColumnIndexOrThrow("period_year"));
        p.periodMonth = c.getInt(c.getColumnIndexOrThrow("period_month"));
        p.house = c.getString(c.getColumnIndexOrThrow("house"));
        p.note = c.getString(c.getColumnIndexOrThrow("note"));
        p.paid = c.getInt(c.getColumnIndexOrThrow("paid")) == 1;
        int paidIdx = c.getColumnIndexOrThrow("paid_at");
        p.paidAt = c.isNull(paidIdx) ? null : c.getLong(paidIdx);
        p.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return p;
    }
}
