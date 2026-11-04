package com.example.taskmgr.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.DatabaseHelper;
import com.example.taskmgr.model.ReferenceEntry;

import java.util.ArrayList;
import java.util.List;

public class ReferenceDao {
    private final DatabaseHelper dbHelper;

    public ReferenceDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public long insert(ReferenceEntry r) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("tab_id", r.tabId);
        cv.put("title", r.title);
        cv.put("value", r.value);
        cv.put("category", r.category);
        return db.insert("reference_entries", null, cv);
    }

    public List<ReferenceEntry> getByTab(long tabId) {
        List<ReferenceEntry> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("reference_entries", null, "tab_id = ?",
                new String[]{String.valueOf(tabId)},
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
        c.close();
        return result;
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("reference_entries", "id = ?", new String[]{String.valueOf(id)});
    }
}