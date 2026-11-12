// com/example/taskmgr/data/dao/SubscriptionDao.java
package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.Subscription;
import com.example.taskmgr.util.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SubscriptionDao {
    private final DatabaseHelper dbHelper;

    public SubscriptionDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(Subscription s) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", s.name);
        cv.put("amount", s.amount);
        cv.put("charge_day", s.chargeDay);
        cv.put("next_charge_at", s.nextChargeAt);
        if (s.description != null) cv.put("description", s.description);
        else cv.putNull("description");
        cv.put("is_active", s.isActive ? 1 : 0);
        cv.put("created_at", s.createdAt);
        return db.insert("subscriptions", null, cv);
    }

    public void update(Subscription s) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", s.name);
        cv.put("amount", s.amount);
        cv.put("charge_day", s.chargeDay);
        cv.put("next_charge_at", s.nextChargeAt);
        if (s.description != null) cv.put("description", s.description);
        else cv.putNull("description");
        cv.put("is_active", s.isActive ? 1 : 0);
        db.update("subscriptions", cv, "id = ?", new String[]{String.valueOf(s.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("subscriptions", "id = ?", new String[]{String.valueOf(id)});
    }

    public void markPaid(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try (Cursor c = db.query("subscriptions", null, "id = ?",
            new String[]{String.valueOf(id)}, null, null, null)) {
            if (!c.moveToFirst()) return;
            long nextAt = c.getLong(c.getColumnIndexOrThrow("next_charge_at"));
            int day = c.getInt(c.getColumnIndexOrThrow("charge_day"));
            long newNext = addMonthsClamped(nextAt, 1, day);
            ContentValues cv = new ContentValues();
            cv.put("next_charge_at", newNext);
            db.update("subscriptions", cv, "id = ?", new String[]{String.valueOf(id)});
        }
    }

    public List<Subscription> getAllSorted() {
        List<Subscription> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String order = "is_active DESC, " +
            "CASE WHEN is_active = 1 THEN next_charge_at END ASC, " +
            "CASE WHEN is_active = 0 THEN name END COLLATE NOCASE ASC";
        try (Cursor c = db.query("subscriptions", null, null, null, null, null, order)) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public Stats getStats() {
        Stats st = new Stats();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT COUNT(*), SUM(amount), " +
                "SUM(CASE WHEN is_active = 1 THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN is_active = 1 THEN amount ELSE 0 END) " +
                "FROM subscriptions", null)) {
            if (c.moveToFirst()) {
                st.total = c.getInt(0);
                st.totalSum = c.isNull(1) ? 0 : c.getDouble(1);
                st.activeCount = c.isNull(2) ? 0 : c.getInt(2);
                st.activeSum = c.isNull(3) ? 0 : c.getDouble(3);
            }
        }
        return st;
    }

    public List<Subscription> getUpcomingWithin(long fromMs, long toMs) {
        List<Subscription> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("subscriptions", null,
            "is_active = 1 AND next_charge_at >= ? AND next_charge_at <= ?",
            new String[]{String.valueOf(fromMs), String.valueOf(toMs)},
            null, null, "next_charge_at ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    // ---- helpers ----

    public static long computeNextChargeAt(int chargeDay) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(chargeDay, maxDay));
        long candidate = c.getTimeInMillis();
        if (candidate < DateUtils.startOfDay(System.currentTimeMillis())) {
            // следующий месяц
            c.add(Calendar.MONTH, 1);
            c.set(Calendar.DAY_OF_MONTH, Math.min(chargeDay,
                c.getActualMaximum(Calendar.DAY_OF_MONTH)));
            candidate = c.getTimeInMillis();
        }
        return candidate;
    }

    public static long addMonthsClamped(long baseMs, int months, int chargeDay) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(baseMs);
        c.add(Calendar.MONTH, months);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(chargeDay, maxDay));
        return c.getTimeInMillis();
    }

    private Subscription fromCursor(Cursor c) {
        Subscription s = new Subscription();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.name = c.getString(c.getColumnIndexOrThrow("name"));
        s.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        s.chargeDay = c.getInt(c.getColumnIndexOrThrow("charge_day"));
        s.nextChargeAt = c.getLong(c.getColumnIndexOrThrow("next_charge_at"));
        s.description = c.getString(c.getColumnIndexOrThrow("description"));
        s.isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1;
        s.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return s;
    }

    public static class Stats {
        public int total;
        public double totalSum;
        public int activeCount;
        public double activeSum;
    }
}
