package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.UtilityReading;

import java.util.ArrayList;
import java.util.List;

public class UtilityReadingDao {
    private final DatabaseHelper dbHelper;

    public UtilityReadingDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(UtilityReading r) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return insert(db, r);
    }

    public long insert(SQLiteDatabase db, UtilityReading r) {
        ContentValues cv = new ContentValues();
        cv.put("period_id", r.periodId);
        cv.put("meter_id", r.meterId);
        cv.put("previous_value", r.previousValue);
        cv.put("current_value", r.currentValue);
        cv.put("consumption", r.consumption);
        cv.put("rate_applied", r.rateApplied);
        cv.put("amount", r.amount);
        cv.put("created_at", r.createdAt);
        return db.insertWithOnConflict("utility_readings", null, cv,
            SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("utility_readings", "id = ?", new String[]{String.valueOf(id)});
    }

    public UtilityReading getByPeriodAndMeter(long periodId, long meterId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("utility_readings", null,
            "period_id = ? AND meter_id = ?",
            new String[]{String.valueOf(periodId), String.valueOf(meterId)},
            null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<UtilityReading> getByPeriod(long periodId) {
        List<UtilityReading> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("utility_readings", null,
            "period_id = ?", new String[]{String.valueOf(periodId)},
            null, null, null)) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    /**
     * Последнее текущее показание для счётчика — для предзаполнения "предыдущего".
     */
    public double getLastCurrentValue(long meterId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT r.current_value FROM utility_readings r " +
            "JOIN utility_periods p ON p.id = r.period_id " +
            "WHERE r.meter_id = ? " +
            "ORDER BY p.period_year DESC, p.period_month DESC " +
            "LIMIT 1";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(meterId)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    private UtilityReading fromCursor(Cursor c) {
        UtilityReading r = new UtilityReading();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.periodId = c.getLong(c.getColumnIndexOrThrow("period_id"));
        r.meterId = c.getLong(c.getColumnIndexOrThrow("meter_id"));
        r.previousValue = c.getDouble(c.getColumnIndexOrThrow("previous_value"));
        r.currentValue = c.getDouble(c.getColumnIndexOrThrow("current_value"));
        r.consumption = c.getDouble(c.getColumnIndexOrThrow("consumption"));
        r.rateApplied = c.getDouble(c.getColumnIndexOrThrow("rate_applied"));
        r.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        r.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return r;
    }
}
