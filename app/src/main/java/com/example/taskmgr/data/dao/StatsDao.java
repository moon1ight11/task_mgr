package com.example.taskmgr.data.dao;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Все метрики статистики. Приватные разделы/вкладки исключены на уровне SQL.
 */
public class StatsDao {

    private final DatabaseHelper dbHelper;

    public StatsDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    /** Условие «задача не из приватного раздела». */
    private static final String NOT_PRIVATE =
            "tab_id NOT IN (" +
                    "  SELECT tb.id FROM tabs tb " +
                    "  JOIN sections s ON s.id = tb.section_id " +
                    "  WHERE s.is_private = 1)";

    // ============ Сводка ============

    public static class Summary {
        public int total;      // всего задач
        public int active;     // активных
        public int done;       // выполненных
        public int overdue;    // просрочено сейчас (по датам)
        public int percentDone() {
            int denom = done + active;
            if (denom == 0) return 0;
            return Math.round(100f * done / denom);
        }
    }

    public Summary getSummary() {
        Summary s = new Summary();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT " +
                            "COUNT(*), " +
                            "SUM(CASE WHEN status='active' THEN 1 ELSE 0 END), " +
                            "SUM(CASE WHEN status='done' THEN 1 ELSE 0 END), " +
                            "SUM(CASE WHEN status='active' AND ( " +
                            "     (due_type='exact_date' AND due_date IS NOT NULL AND due_date < ?) " +
                            "     OR (due_type='range' AND due_to IS NOT NULL AND due_to < ?) " +
                            ") THEN 1 ELSE 0 END) " +
                            "FROM tasks WHERE " + NOT_PRIVATE,
                    new String[]{String.valueOf(System.currentTimeMillis()),
                            String.valueOf(System.currentTimeMillis())});
            if (c.moveToFirst()) {
                s.total = c.getInt(0);
                s.active = c.isNull(1) ? 0 : c.getInt(1);
                s.done = c.isNull(2) ? 0 : c.getInt(2);
                s.overdue = c.isNull(3) ? 0 : c.getInt(3);
            }
        } finally {
            if (c != null) c.close();
        }
        return s;
    }

    // ============ По приоритетам (активные) ============

    public static class PriorityRow {
        public String priority; // important / normal / whatever
        public int active;
    }

    public List<PriorityRow> getActiveByPriority() {
        List<PriorityRow> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT priority, COUNT(*) FROM tasks " +
                            "WHERE status='active' AND " + NOT_PRIVATE +
                            " GROUP BY priority",
                    null);
            while (c.moveToNext()) {
                PriorityRow r = new PriorityRow();
                r.priority = c.getString(0);
                r.active = c.getInt(1);
                result.add(r);
            }
        } finally {
            if (c != null) c.close();
        }
        // Дополняем отсутствующие приоритеты нулями, чтобы в UI были все 3
        for (String p : new String[]{"important", "normal", "whatever"}) {
            boolean found = false;
            for (PriorityRow r : result) if (p.equals(r.priority)) { found = true; break; }
            if (!found) {
                PriorityRow r = new PriorityRow();
                r.priority = p;
                r.active = 0;
                result.add(r);
            }
        }
        return result;
    }

    // ============ По разделам ============

    public static class SectionRow {
        public long id;
        public String name;
        public int active;
        public int done;
        public int percentDone() {
            int denom = done + active;
            if (denom == 0) return 0;
            return Math.round(100f * done / denom);
        }
    }

    /** Разбивка по разделам (приватные исключены). Сортировка — по активным убыв. */
    public List<SectionRow> getBySection() {
        List<SectionRow> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT s.id, s.name, " +
                            "  SUM(CASE WHEN t.status='active' THEN 1 ELSE 0 END), " +
                            "  SUM(CASE WHEN t.status='done'   THEN 1 ELSE 0 END) " +
                            "FROM sections s " +
                            "LEFT JOIN tabs tb ON tb.section_id = s.id " +
                            "LEFT JOIN tasks t ON t.tab_id = tb.id " +
                            "WHERE s.is_private = 0 " +
                            "GROUP BY s.id, s.name " +
                            "ORDER BY 3 DESC, 4 DESC",
                    null);
            while (c.moveToNext()) {
                SectionRow r = new SectionRow();
                r.id = c.getLong(0);
                r.name = c.getString(1);
                r.active = c.isNull(2) ? 0 : c.getInt(2);
                r.done = c.isNull(3) ? 0 : c.getInt(3);
                result.add(r);
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    // ============ Выполнено за период ============

    public static class PeriodStats {
        public int completions;
        public int days;
        public float perDay() {
            if (days <= 0) return 0f;
            return (float) completions / days;
        }
    }

    /** Сколько выполнений (записей history) за последние N дней, только не-приватные задачи. */
    public PeriodStats getCompletionsForPeriod(int days) {
        PeriodStats p = new PeriodStats();
        p.days = days;
        long since = System.currentTimeMillis() - (long) days * 24L * 60 * 60 * 1000;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT COUNT(*) FROM history h " +
                            "WHERE h.completed_at >= ? " +
                            "AND h.task_id IN ( " +
                            "  SELECT t.id FROM tasks t WHERE " + NOT_PRIVATE + ")",
                    new String[]{String.valueOf(since)});
            if (c.moveToFirst()) p.completions = c.getInt(0);
        } finally {
            if (c != null) c.close();
        }
        return p;
    }

    // ============ Покупки ============

    public static class ShoppingStats {
        public int total;
        public int bought;
        public int notBought;
        public double notBoughtSum;
    }

    public ShoppingStats getShoppingStats() {
        ShoppingStats s = new ShoppingStats();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT COUNT(*), " +
                            "SUM(CASE WHEN is_bought=1 THEN 1 ELSE 0 END), " +
                            "SUM(CASE WHEN is_bought=0 THEN 1 ELSE 0 END), " +
                            "SUM(CASE WHEN is_bought=0 THEN COALESCE(price,0) ELSE 0 END) " +
                            "FROM shopping_items WHERE " + NOT_PRIVATE,
                    null);
            if (c.moveToFirst()) {
                s.total = c.getInt(0);
                s.bought = c.isNull(1) ? 0 : c.getInt(1);
                s.notBought = c.isNull(2) ? 0 : c.getInt(2);
                s.notBoughtSum = c.isNull(3) ? 0 : c.getDouble(3);
            }
        } finally {
            if (c != null) c.close();
        }
        return s;
    }
}