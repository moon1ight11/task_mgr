package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.ReferenceEntry;

import java.util.ArrayList;
import java.util.List;

public class ReferenceDao {
    private final DatabaseHelper dbHelper;

    public ReferenceDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(ReferenceEntry r) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("tab_id", r.tabId);
        cv.put("title", r.title);
        cv.put("value", r.value);
        if (r.category != null) cv.put("category", r.category);
        else cv.putNull("category");
        return db.insert("reference_entries", null, cv);
    }

    /** Все записи вкладки (без фильтра). */
    public List<ReferenceEntry> getByTab(long tabId) {
        return queryByCategory(tabId, null);
    }

    /**
     * Записи вкладки с фильтром по категории.
     * @param category null или пустая строка = «все категории»
     */
    public List<ReferenceEntry> getByTabAndCategory(long tabId, String category) {
        return queryByCategory(tabId, category);
    }

    /**
     * Уникальные непустые категории вкладки, отсортированы по алфавиту.
     * Нужны для построения чипов-фильтров.
     */
    public List<String> getCategories(long tabId) {
        List<String> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT DISTINCT category FROM reference_entries " +
                            "WHERE tab_id = ? AND category IS NOT NULL AND category <> '' " +
                            "ORDER BY category COLLATE NOCASE ASC",
                    new String[]{String.valueOf(tabId)});
            while (c.moveToNext()) {
                result.add(c.getString(0));
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public void update(ReferenceEntry r) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", r.title);
        cv.put("value", r.value);
        if (r.category != null) cv.put("category", r.category);
        else cv.putNull("category");
        db.update("reference_entries", cv, "id = ?", new String[]{String.valueOf(r.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("reference_entries", "id = ?", new String[]{String.valueOf(id)});
    }

    private List<ReferenceEntry> queryByCategory(long tabId, String category) {
        List<ReferenceEntry> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            String where;
            String[] args;
            if (category == null || category.trim().isEmpty()) {
                where = "tab_id = ?";
                args = new String[]{String.valueOf(tabId)};
            } else {
                where = "tab_id = ? AND category = ?";
                args = new String[]{String.valueOf(tabId), category};
            }
            c = db.query("reference_entries", null, where, args,
                    null, null, "category ASC, title ASC");
            while (c.moveToNext()) {
                ReferenceEntry r = new ReferenceEntry();
                r.id = c.getLong(c.getColumnIndexOrThrow("id"));
                r.tabId = c.getLong(c.getColumnIndexOrThrow("tab_id"));
                r.title = c.getString(c.getColumnIndexOrThrow("title"));
                r.value = c.getString(c.getColumnIndexOrThrow("value"));
                r.category = c.getString(c.getColumnIndexOrThrow("category"));
                result.add(r);
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }
}