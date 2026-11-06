package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.Section;

import java.util.ArrayList;
import java.util.List;

public class SectionDao {
    public static final String AUTO_SECTION_NAME = "Авто";
    private final DatabaseHelper dbHelper;

    public SectionDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(Section s) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", s.name);
        if (s.icon != null) cv.put("icon", s.icon);
        else cv.putNull("icon");
        cv.put("is_private", s.isPrivate ? 1 : 0);
        cv.put("sort_order", s.sortOrder);
        return db.insert("sections", null, cv);
    }

    public List<Section> getAll() {
        List<Section> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("sections", null, null, null, null, null, "sort_order ASC");
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public Section getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("sections", null, "id = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            return c.moveToFirst() ? fromCursor(c) : null;
        } finally {
            if (c != null) c.close();
        }
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("sections", "id = ?", new String[]{String.valueOf(id)});
    }

    private Section fromCursor(Cursor c) {
        Section s = new Section();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.name = c.getString(c.getColumnIndexOrThrow("name"));
        s.icon = c.getString(c.getColumnIndexOrThrow("icon"));
        int privIdx = c.getColumnIndexOrThrow("is_private");
        s.isPrivate = !c.isNull(privIdx) && c.getInt(privIdx) == 1;
        s.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
        return s;
    }
}