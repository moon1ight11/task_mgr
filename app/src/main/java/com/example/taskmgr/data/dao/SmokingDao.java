package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.SmokingEntry;
import com.example.taskmgr.service.SmokingStats;
import com.example.taskmgr.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

public class SmokingDao {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final DatabaseHelper dbHelper;

    public SmokingDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(SmokingEntry entry) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("smoked_at", entry.smokedAt);
        cv.put("count", Math.max(1, entry.count));
        if (entry.note != null && !entry.note.isEmpty()) cv.put("note", entry.note);
        else cv.putNull("note");
        return db.insert("smoking_log", null, cv);
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("smoking_log", "id = ?", new String[]{String.valueOf(id)});
    }

    public void deleteAll() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("smoking_log", null, null);
    }

    public List<SmokingEntry> getRecent(int limit) {
        List<SmokingEntry> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("smoking_log", null, null, null, null, null,
            "smoked_at DESC", String.valueOf(limit))) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public SmokingStats getStats() {
        SmokingStats s = new SmokingStats();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long startOfToday = DateUtils.startOfDay(System.currentTimeMillis());

        try (Cursor c = db.rawQuery(
            "SELECT COALESCE(SUM(count), 0), COUNT(*), MIN(smoked_at), MAX(smoked_at) " +
                "FROM smoking_log", null)) {
            if (c.moveToFirst()) {
                s.totalSmoked = c.getInt(0);
                s.entriesCount = c.getInt(1);
                if (!c.isNull(2)) s.firstSmokedAt = c.getLong(2);
                if (!c.isNull(3)) s.lastSmokedAt = c.getLong(3);
            }
        }

        try (Cursor c = db.rawQuery(
            "SELECT COALESCE(SUM(count), 0) FROM smoking_log WHERE smoked_at >= ?",
            new String[]{String.valueOf(startOfToday)})) {
            if (c.moveToFirst()) s.todaySmoked = c.getInt(0);
        }

        try (Cursor c = db.rawQuery(
            "SELECT COALESCE(MAX(day_total), 0) FROM (" +
                "  SELECT SUM(count) AS day_total FROM smoking_log" +
                "  GROUP BY date(smoked_at / 1000, 'unixepoch', 'localtime')" +
                ")", null)) {
            if (c.moveToFirst()) s.maxPerDay = c.getInt(0);
        }

        if (s.lastSmokedAt != null) {
            long lastDay = DateUtils.startOfDay(s.lastSmokedAt);
            s.daysWithout = (int) Math.max(0, Math.round((startOfToday - lastDay) / (double) DAY_MS));
        }

        if (s.firstSmokedAt != null) {
            long firstDay = DateUtils.startOfDay(s.firstSmokedAt);
            int trackedDays = (int) Math.max(1,
                Math.round((startOfToday - firstDay) / (double) DAY_MS) + 1);
            s.avgPerDay = Math.round((float) s.totalSmoked / trackedDays);
        }

        return s;
    }

    private SmokingEntry fromCursor(Cursor c) {
        SmokingEntry e = new SmokingEntry();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.smokedAt = c.getLong(c.getColumnIndexOrThrow("smoked_at"));
        e.count = c.getInt(c.getColumnIndexOrThrow("count"));
        int noteIdx = c.getColumnIndex("note");
        if (noteIdx >= 0 && !c.isNull(noteIdx)) e.note = c.getString(noteIdx);
        return e;
    }
}
