package com.example.taskmgr.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.DatabaseHelper;
import com.example.taskmgr.model.Section;

import java.util.ArrayList;
import java.util.List;

public class SectionDao {
    private final DatabaseHelper dbHelper;

    public SectionDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public long insert(Section s) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", s.name);
        cv.put("icon", s.icon);
        cv.put("sort_order", s.sortOrder);
        return db.insert("sections", null, cv);
    }

    public List<Section> getAll() {
        List<Section> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("sections", null, null, null, null, null, "sort_order ASC");
        while (c.moveToNext()) {
            Section s = new Section();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.name = c.getString(c.getColumnIndexOrThrow("name"));
            s.icon = c.getString(c.getColumnIndexOrThrow("icon"));
            s.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
            result.add(s);
        }
        c.close();
        return result;
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("sections", "id = ?", new String[]{String.valueOf(id)});
    }
}