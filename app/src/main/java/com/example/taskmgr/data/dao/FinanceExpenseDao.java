package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.FinanceExpense;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

public class FinanceExpenseDao {
    private final DatabaseHelper dbHelper;

    public FinanceExpenseDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(FinanceExpense e) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("period_id", e.periodId);
        cv.put("category", e.category);
        if (e.title != null) cv.put("title", e.title);
        else cv.putNull("title");
        cv.put("amount", e.amount);
        cv.put("created_at", e.createdAt);
        cv.put("source_type", e.sourceType != null ? e.sourceType : "manual");
        if (e.sourceRefId != null) cv.put("source_ref_id", e.sourceRefId);
        else cv.putNull("source_ref_id");
        if (e.sourceRefs != null) cv.put("source_refs", e.sourceRefs); // NEW
        else cv.putNull("source_refs");
        return db.insert("finance_expenses", null, cv);
    }

    public void update(FinanceExpense e) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("category", e.category);
        if (e.title != null) cv.put("title", e.title);
        else cv.putNull("title");
        cv.put("amount", e.amount);
        db.update("finance_expenses", cv, "id = ?", new String[]{String.valueOf(e.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("finance_expenses", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<FinanceExpense> getByPeriod(long periodId) {
        List<FinanceExpense> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("finance_expenses", null, "period_id = ?",
            new String[]{String.valueOf(periodId)}, null, null,
            "created_at ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public double sumByPeriod(long periodId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT COALESCE(SUM(amount), 0) FROM finance_expenses WHERE period_id = ?",
            new String[]{String.valueOf(periodId)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public List<String> getUsedCategories() {
        List<String> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT DISTINCT category FROM finance_expenses " +
                "WHERE category IS NOT NULL AND category <> '' " +
                "ORDER BY category COLLATE NOCASE ASC", null)) {
            while (c.moveToNext()) result.add(c.getString(0));
        }
        return result;
    }

    /**
     * Возвращает id подписок, уже вошедших в bundle-записи этого периода.
     */
    public Set<Long> getSubscriptionBundleRefIds(long periodId) {
        Set<Long> result = new HashSet<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT source_refs FROM finance_expenses " +
                "WHERE period_id = ? AND source_type = 'subscription_bundle' " +
                "AND source_refs IS NOT NULL",
            new String[]{String.valueOf(periodId)})) {
            while (c.moveToNext()) {
                String refs = c.getString(0);
                if (refs == null || refs.isEmpty()) continue;
                for (String part : refs.split(",")) {
                    try {
                        result.add(Long.parseLong(part.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return result;
    }

    /**
     * Старый метод — для записей, где source_type='subscription' и один source_ref_id.
     * Оставлен на случай, если такие записи остались в БД.
     */
    public Set<Long> getSubscriptionRefIdsForPeriod(long periodId) {
        Set<Long> result = new HashSet<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT source_ref_id FROM finance_expenses " +
                "WHERE period_id = ? AND source_type = 'subscription' " +
                "AND source_ref_id IS NOT NULL",
            new String[]{String.valueOf(periodId)})) {
            while (c.moveToNext()) result.add(c.getLong(0));
        }
        return result;
    }

    private FinanceExpense fromCursor(Cursor c) {
        FinanceExpense e = new FinanceExpense();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.periodId = c.getLong(c.getColumnIndexOrThrow("period_id"));
        e.category = c.getString(c.getColumnIndexOrThrow("category"));
        e.title = c.getString(c.getColumnIndexOrThrow("title"));
        e.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        e.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        e.sourceType = c.getString(c.getColumnIndexOrThrow("source_type"));
        int refIdx = c.getColumnIndex("source_ref_id");
        if (refIdx >= 0 && !c.isNull(refIdx)) e.sourceRefId = c.getLong(refIdx);
        int refsIdx = c.getColumnIndex("source_refs"); // NEW
        if (refsIdx >= 0 && !c.isNull(refsIdx)) e.sourceRefs = c.getString(refsIdx);
        return e;
    }

    public Set<Long> getShoppingBundleRefIds(long periodId) {
        Set<Long> result = new HashSet<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT source_refs FROM finance_expenses " +
                "WHERE period_id = ? AND source_type = 'shopping_bundle' " +
                "AND source_refs IS NOT NULL",
            new String[]{String.valueOf(periodId)})) {
            while (c.moveToNext()) {
                String refs = c.getString(0);
                if (refs == null || refs.isEmpty()) continue;
                for (String part : refs.split(",")) {
                    try { result.add(Long.parseLong(part.trim())); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return result;
    }

    public Map<String, Double> getCategoryTotals(long fromMillis, long toMillis) {
        Map<String, Double> result = new java.util.LinkedHashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT category, SUM(amount) FROM finance_expenses " +
            "WHERE period_id IN (SELECT id FROM finance_periods WHERE start_at >= ? AND start_at < ?) " +
            "GROUP BY category ORDER BY 2 DESC";
        try (Cursor c = db.rawQuery(sql, new String[]{
            String.valueOf(fromMillis), String.valueOf(toMillis)})) {
            while (c.moveToNext()) result.put(c.getString(0), c.getDouble(1));
        }
        return result;
    }

    public double getTotalSpent(long fromMillis, long toMillis) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM finance_expenses " +
            "WHERE period_id IN (SELECT id FROM finance_periods WHERE start_at >= ? AND start_at < ?)";
        try (Cursor c = db.rawQuery(sql, new String[]{
            String.valueOf(fromMillis), String.valueOf(toMillis)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }
}
