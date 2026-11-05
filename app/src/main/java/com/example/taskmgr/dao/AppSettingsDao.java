package com.example.taskmgr.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.DatabaseHelper;

public class AppSettingsDao {

    public static final String KEY_PRIVATE_PWD_HASH = "private_pwd_hash";
    public static final String KEY_PRIVATE_PWD_SALT = "private_pwd_salt";
    public static final String KEY_PRIVATE_PWD_ITER = "private_pwd_iter";

    private final DatabaseHelper dbHelper;

    public AppSettingsDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public String get(String key) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("app_settings", new String[]{"value"},
                    "key = ?", new String[]{key}, null, null, null);
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            if (c != null) c.close();
        }
    }

    public void put(String key, String value) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean hasPrivatePassword() {
        String h = get(KEY_PRIVATE_PWD_HASH);
        String s = get(KEY_PRIVATE_PWD_SALT);
        return h != null && !h.isEmpty() && s != null && !s.isEmpty();
    }
}