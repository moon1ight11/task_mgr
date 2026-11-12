// com/example/taskmgr/data/dao/FinancePeriodDao.java
package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.FinancePeriod;

import java.util.ArrayList;
import java.util.List;

public class FinancePeriodDao {
    private final DatabaseHelper dbHelper;

    public FinancePeriodDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(FinancePeriod p) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("start_at", p.startAt);
        cv.put("end_at", p.endAt);
        cv.put("name", p.name);
        cv.put("total_income", p.totalIncome);
        if (p.note != null) cv.put("note", p.note);
        else cv.putNull("note");
        cv.put("created_at", p.createdAt);
        return db.insertWithOnConflict("finance_periods", null, cv,
            SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void update(FinancePeriod p) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("end_at", p.endAt);
        cv.put("name", p.name);
        cv.put("total_income", p.totalIncome);
        if (p.note != null) cv.put("note", p.note);
        else cv.putNull("note");
        db.update("finance_periods", cv, "id = ?", new String[]{String.valueOf(p.id)});
    }

    public List<FinancePeriod> getSince(long sinceMillis) {
        List<FinancePeriod> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("finance_periods", null,
            "start_at >= ?", new String[]{String.valueOf(sinceMillis)},
            null, null, "start_at ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("finance_periods", "id = ?", new String[]{String.valueOf(id)});
    }

    public FinancePeriod findByStart(long startAt) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("finance_periods", null, "start_at = ?",
            new String[]{String.valueOf(startAt)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<FinancePeriod> getAll() {
        List<FinancePeriod> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("finance_periods", null, null, null, null, null,
            "start_at ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public FinancePeriod findCurrent(long now) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("finance_periods", null,
            "start_at <= ? AND end_at > ?",
            new String[]{String.valueOf(now), String.valueOf(now)},
            null, null, "start_at DESC", "1")) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    private FinancePeriod fromCursor(Cursor c) {
        FinancePeriod p = new FinancePeriod();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.startAt = c.getLong(c.getColumnIndexOrThrow("start_at"));
        p.endAt = c.getLong(c.getColumnIndexOrThrow("end_at"));
        p.name = c.getString(c.getColumnIndexOrThrow("name"));
        p.totalIncome = c.getDouble(c.getColumnIndexOrThrow("total_income"));
        p.note = c.getString(c.getColumnIndexOrThrow("note"));
        p.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return p;
    }
}
