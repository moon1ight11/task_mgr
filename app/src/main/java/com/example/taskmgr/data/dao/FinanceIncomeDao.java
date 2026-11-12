// com/example/taskmgr/data/dao/FinanceIncomeDao.java
package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.FinanceIncome;

import java.util.ArrayList;
import java.util.List;

public class FinanceIncomeDao {
    private final DatabaseHelper dbHelper;

    public FinanceIncomeDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(FinanceIncome i) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        if (i.name != null) cv.put("name", i.name);
        else cv.putNull("name");
        cv.put("amount", i.amount);
        cv.put("day_of_month", i.dayOfMonth);
        cv.put("is_active", i.isActive ? 1 : 0);
        cv.put("sort_order", i.sortOrder);
        cv.put("created_at", i.createdAt);
        return db.insert("finance_incomes", null, cv);
    }

    public void update(FinanceIncome i) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        if (i.name != null) cv.put("name", i.name);
        else cv.putNull("name");
        cv.put("amount", i.amount);
        cv.put("day_of_month", i.dayOfMonth);
        cv.put("is_active", i.isActive ? 1 : 0);
        db.update("finance_incomes", cv, "id = ?", new String[]{String.valueOf(i.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("finance_incomes", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<FinanceIncome> getAll(boolean activeOnly) {
        List<FinanceIncome> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String where = activeOnly ? "is_active = 1" : null;
        try (Cursor c = db.query("finance_incomes", null, where, null, null, null,
            "sort_order ASC, day_of_month ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    private FinanceIncome fromCursor(Cursor c) {
        FinanceIncome i = new FinanceIncome();
        i.id = c.getLong(c.getColumnIndexOrThrow("id"));
        i.name = c.getString(c.getColumnIndexOrThrow("name"));
        i.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        i.dayOfMonth = c.getInt(c.getColumnIndexOrThrow("day_of_month"));
        i.isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1;
        i.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
        i.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return i;
    }
}
