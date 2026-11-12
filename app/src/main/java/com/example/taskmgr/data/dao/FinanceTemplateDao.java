// com/example/taskmgr/data/dao/FinanceTemplateDao.java
package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.FinanceTemplate;

import java.util.ArrayList;
import java.util.List;

public class FinanceTemplateDao {
    private final DatabaseHelper dbHelper;

    public FinanceTemplateDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(FinanceTemplate t) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("category", t.category);
        if (t.title != null) cv.put("title", t.title);
        else cv.putNull("title");
        cv.put("default_amount", t.defaultAmount);
        cv.put("is_active", t.isActive ? 1 : 0);
        cv.put("sort_order", t.sortOrder);
        cv.put("created_at", t.createdAt);
        return db.insert("finance_templates", null, cv);
    }

    public void update(FinanceTemplate t) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("category", t.category);
        if (t.title != null) cv.put("title", t.title);
        else cv.putNull("title");
        cv.put("default_amount", t.defaultAmount);
        cv.put("is_active", t.isActive ? 1 : 0);
        db.update("finance_templates", cv, "id = ?", new String[]{String.valueOf(t.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("finance_templates", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<FinanceTemplate> getAll(boolean activeOnly) {
        List<FinanceTemplate> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String where = activeOnly ? "is_active = 1" : null;
        try (Cursor c = db.query("finance_templates", null, where, null, null, null,
            "sort_order ASC, category COLLATE NOCASE ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    private FinanceTemplate fromCursor(Cursor c) {
        FinanceTemplate t = new FinanceTemplate();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.category = c.getString(c.getColumnIndexOrThrow("category"));
        t.title = c.getString(c.getColumnIndexOrThrow("title"));
        t.defaultAmount = c.getDouble(c.getColumnIndexOrThrow("default_amount"));
        t.isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1;
        t.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
        t.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return t;
    }
}
