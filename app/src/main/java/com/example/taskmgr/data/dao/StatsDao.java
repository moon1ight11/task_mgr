package com.example.taskmgr.data.dao;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

public class StatsDao {

    private static final String NOT_PRIVATE =
        "tab_id NOT IN (" +
            "  SELECT tb.id FROM tabs tb " +
            "  JOIN sections s ON s.id = tb.section_id " +
            "  WHERE s.is_private = 1)";
    private final DatabaseHelper dbHelper;

    public StatsDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public Summary getSummary() {
        Summary s = new Summary();
        long startOfToday = DateUtils.startOfDay(System.currentTimeMillis());
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT " +
                "COUNT(*), " +
                "SUM(CASE WHEN status='active' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status='done' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status='active' AND ( " +
                "     (due_type='exact_date' AND due_date IS NOT NULL AND due_date < ?) " +
                "     OR (due_type='range' AND due_to IS NOT NULL AND due_to < ?) " +
                ") THEN 1 ELSE 0 END) " +
                "FROM tasks WHERE " + NOT_PRIVATE,
            new String[]{String.valueOf(startOfToday),
                String.valueOf(startOfToday)})) {
            if (c.moveToFirst()) {
                s.total = c.getInt(0);
                s.active = c.isNull(1) ? 0 : c.getInt(1);
                s.done = c.isNull(2) ? 0 : c.getInt(2);
                s.overdue = c.isNull(3) ? 0 : c.getInt(3);
            }
        }
        return s;
    }

    public List<PriorityRow> getActiveByPriority() {
        List<PriorityRow> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT priority, COUNT(*) FROM tasks " +
                "WHERE status='active' AND " + NOT_PRIVATE +
                " GROUP BY priority",
            null)) {
            while (c.moveToNext()) {
                PriorityRow r = new PriorityRow();
                r.priority = c.getString(0);
                r.active = c.getInt(1);
                result.add(r);
            }
        }
        for (String p : new String[]{"important", "normal", "whatever"}) {
            boolean found = false;
            for (PriorityRow r : result)
                if (p.equals(r.priority)) {
                    found = true;
                    break;
                }
            if (!found) {
                PriorityRow r = new PriorityRow();
                r.priority = p;
                r.active = 0;
                result.add(r);
            }
        }
        return result;
    }

    public List<SectionRow> getBySection() {
        List<SectionRow> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT s.id, s.name, " +
                "  SUM(CASE WHEN t.status='active' THEN 1 ELSE 0 END), " +
                "  SUM(CASE WHEN t.status='done'   THEN 1 ELSE 0 END) " +
                "FROM sections s " +
                "LEFT JOIN tabs tb ON tb.section_id = s.id " +
                "LEFT JOIN tasks t ON t.tab_id = tb.id " +
                "WHERE s.is_private = 0 " +
                "GROUP BY s.id, s.name " +
                "ORDER BY 3 DESC, 4 DESC",
            null)) {
            while (c.moveToNext()) {
                SectionRow r = new SectionRow();
                r.id = c.getLong(0);
                r.name = c.getString(1);
                r.active = c.isNull(2) ? 0 : c.getInt(2);
                r.done = c.isNull(3) ? 0 : c.getInt(3);
                result.add(r);
            }
        }
        return result;
    }

    public PeriodStats getCompletionsForPeriod(int days) {
        PeriodStats p = new PeriodStats();
        p.days = days;
        long since = System.currentTimeMillis() - (long) days * 24L * 60 * 60 * 1000;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT COUNT(*) FROM history h " +
                "WHERE h.completed_at >= ? " +
                "AND h.task_id IN ( " +
                "  SELECT t.id FROM tasks t WHERE " + NOT_PRIVATE + ")",
            new String[]{String.valueOf(since)})) {
            if (c.moveToFirst()) p.completions = c.getInt(0);
        }
        return p;
    }

    public CompletionTimeStats getCompletionTimeStats(int days) {
        CompletionTimeStats s = new CompletionTimeStats();
        long since = days > 0
            ? System.currentTimeMillis() - (long) days * 24L * 60 * 60 * 1000
            : 0L;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql =
            "SELECT " +
                "  COUNT(*), " +
                "  AVG(h.completed_at - t.created_at), " +
                "  MIN(h.completed_at - t.created_at), " +
                "  MAX(h.completed_at - t.created_at) " +
                "FROM history h " +
                "JOIN tasks t ON t.id = h.task_id " +
                "WHERE h.completed_at >= ? " +
                "  AND h.completed_at > t.created_at " +
                "  AND t.tab_id NOT IN (" +
                "    SELECT tb.id FROM tabs tb " +
                "    JOIN sections s ON s.id = tb.section_id " +
                "    WHERE s.is_private = 1)";

        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(since)})) {
            if (c.moveToFirst()) {
                s.count = c.isNull(0) ? 0 : c.getInt(0);
                s.avgMillis = c.isNull(1) ? 0 : c.getDouble(1);
                s.minMillis = c.isNull(2) ? 0 : c.getLong(2);
                s.maxMillis = c.isNull(3) ? 0 : c.getLong(3);
            }
        }
        return s;
    }

    public DeadlineStats getDeadlineStats(int days) {
        DeadlineStats s = new DeadlineStats();
        long since = days > 0
            ? System.currentTimeMillis() - (long) days * 24L * 60 * 60 * 1000
            : 0L;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql =
            "SELECT " +
                "  COUNT(*), " +
                "  SUM(CASE WHEN " +
                "    (t.due_type = 'exact_date' AND t.due_date IS NOT NULL AND h.completed_at <= t.due_date) " +
                "    OR (t.due_type = 'range' AND t.due_to IS NOT NULL AND h.completed_at <= t.due_to) " +
                "    THEN 1 ELSE 0 END) " +
                "FROM history h " +
                "JOIN tasks t ON t.id = h.task_id " +
                "WHERE h.completed_at >= ? " +
                "  AND ( " +
                "    (t.due_type = 'exact_date' AND t.due_date IS NOT NULL) " +
                "    OR (t.due_type = 'range' AND t.due_to IS NOT NULL) " +
                "  ) " +
                "  AND t.tab_id NOT IN (" +
                "    SELECT tb.id FROM tabs tb " +
                "    JOIN sections s ON s.id = tb.section_id " +
                "    WHERE s.is_private = 1)";

        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(since)})) {
            if (c.moveToFirst()) {
                s.completedWithDeadline = c.isNull(0) ? 0 : c.getInt(0);
                s.onTime = c.isNull(1) ? 0 : c.getInt(1);
                s.late = s.completedWithDeadline - s.onTime;
            }
        }
        return s;
    }

    public ShoppingStats getShoppingStats() {
        ShoppingStats s = new ShoppingStats();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT COUNT(*), " +
                "SUM(CASE WHEN is_bought=1 THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN is_bought=0 THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN is_bought=0 THEN COALESCE(price,0) ELSE 0 END) " +
                "FROM shopping_items WHERE " + NOT_PRIVATE,
            null)) {
            if (c.moveToFirst()) {
                s.total = c.getInt(0);
                s.bought = c.isNull(1) ? 0 : c.getInt(1);
                s.notBought = c.isNull(2) ? 0 : c.getInt(2);
                s.notBoughtSum = c.isNull(3) ? 0 : c.getDouble(3);
            }
        }
        return s;
    }

    public static class Summary {
        public int total;
        public int active;
        public int done;
        public int overdue;

        public int percentDone() {
            int denominator = done + active;
            if (denominator == 0) return 0;
            return Math.round(100f * done / denominator);
        }
    }

    public static class CompletionTimeStats {
        public int count;
        public double avgMillis;
        public long minMillis;
        public long maxMillis;

        public boolean hasData() {
            return count > 0;
        }
    }

    public static class DeadlineStats {
        public int completedWithDeadline;
        public int onTime;
        public int late;

        public int percentOnTime() {
            if (completedWithDeadline == 0) return 0;
            return Math.round(100f * onTime / completedWithDeadline);
        }

        public boolean hasData() {
            return completedWithDeadline > 0;
        }
    }

    public static class PriorityRow {
        public String priority;
        public int active;
    }

    public static class SectionRow {
        public long id;
        public String name;
        public int active;
        public int done;

        public int percentDone() {
            int denominator = done + active;
            if (denominator == 0) return 0;
            return Math.round(100f * done / denominator);
        }
    }

    public static class PeriodStats {
        public int completions;
        public int days;

        public float perDay() {
            if (days <= 0) return 0f;
            return (float) completions / days;
        }
    }

    public static class ShoppingStats {
        public int total;
        public int bought;
        public int notBought;
        public double notBoughtSum;
    }
}
