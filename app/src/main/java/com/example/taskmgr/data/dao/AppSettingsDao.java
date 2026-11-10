package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;

public class AppSettingsDao {

    public static final String KEY_PRIVATE_PWD_HASH = "private_pwd_hash";
    public static final String KEY_PRIVATE_PWD_SALT = "private_pwd_salt";
    public static final String KEY_PRIVATE_PWD_ITER = "private_pwd_iter";


    public static final String KEY_NOTIF_EVENING_ENABLED = "notif_evening_enabled";
    public static final String KEY_NOTIF_EVENING_HOUR = "notif_evening_hour";
    public static final String KEY_NOTIF_EVENING_MINUTE = "notif_evening_minute";
    public static final String KEY_NOTIF_MORNING_ENABLED = "notif_morning_enabled";
    public static final String KEY_NOTIF_MORNING_HOUR = "notif_morning_hour";
    public static final String KEY_NOTIF_MORNING_MINUTE = "notif_morning_minute";

    public static final int DEFAULT_EVENING_HOUR = 20;
    public static final int DEFAULT_EVENING_MINUTE = 0;
    public static final int DEFAULT_MORNING_HOUR = 9;
    public static final int DEFAULT_MORNING_MINUTE = 0;

    private final DatabaseHelper dbHelper;

    public AppSettingsDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public String get(String key) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("app_settings", new String[]{"value"}, "key = ?", new String[]{key}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
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
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query("app_settings", new String[]{"key", "value"}, "key IN (?, ?)", new String[]{KEY_PRIVATE_PWD_HASH, KEY_PRIVATE_PWD_SALT}, null, null, null)) {
            boolean hasHash = false;
            boolean hasSalt = false;
            while (c.moveToNext()) {
                String key = c.getString(0);
                String value = c.getString(1);
                if (value == null || value.isEmpty()) continue;
                if (KEY_PRIVATE_PWD_HASH.equals(key)) hasHash = true;
                else if (KEY_PRIVATE_PWD_SALT.equals(key)) hasSalt = true;
            }
            return hasHash && hasSalt;
        }
    }
}
