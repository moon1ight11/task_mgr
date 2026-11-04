package com.example.taskmgr;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "taskmgr.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true); // включаем каскадные удаления
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 1. Разделы
        db.execSQL("CREATE TABLE sections (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "icon TEXT, " +
                "sort_order INTEGER DEFAULT 0)");

        // 2. Вкладки
        db.execSQL("CREATE TABLE tabs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "section_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "current_km INTEGER, " +
                "year INTEGER, " +
                "sort_order INTEGER DEFAULT 0, " +
                "FOREIGN KEY(section_id) REFERENCES sections(id) ON DELETE CASCADE)");

        // 3. Задачи
        db.execSQL("CREATE TABLE tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "tab_id INTEGER NOT NULL, " +
                "title TEXT NOT NULL, " +
                "description TEXT, " +
                "created_at INTEGER NOT NULL, " +
                "priority TEXT DEFAULT 'normal', " +
                "status TEXT DEFAULT 'active', " +
                "due_type TEXT DEFAULT 'none', " +
                "due_date INTEGER, " +
                "due_from INTEGER, " +
                "due_to INTEGER, " +
                "due_km INTEGER, " +
                "repeat_rule TEXT DEFAULT 'none', " +
                "repeat_interval_km INTEGER, " +
                "repeat_interval_months INTEGER, " +
                "FOREIGN KEY(tab_id) REFERENCES tabs(id) ON DELETE CASCADE)");

        // 4. История
        db.execSQL("CREATE TABLE history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "task_id INTEGER NOT NULL, " +
                "completed_at INTEGER NOT NULL, " +
                "km_at_completion INTEGER, " +
                "note TEXT, " +
                "FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE)");

        // 5. Покупки
        db.execSQL("CREATE TABLE shopping_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "tab_id INTEGER NOT NULL, " +
                "title TEXT NOT NULL, " +
                "price REAL, " +
                "is_bought INTEGER DEFAULT 0, " +
                "is_selected INTEGER DEFAULT 0, " +
                "created_at INTEGER NOT NULL, " +
                "FOREIGN KEY(tab_id) REFERENCES tabs(id) ON DELETE CASCADE)");

        // 6. Справочник
        db.execSQL("CREATE TABLE reference_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "tab_id INTEGER NOT NULL, " +
                "title TEXT NOT NULL, " +
                "value TEXT NOT NULL, " +
                "category TEXT, " +
                "FOREIGN KEY(tab_id) REFERENCES tabs(id) ON DELETE CASCADE)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // На этапе разработки просто пересоздаём базу
        db.execSQL("DROP TABLE IF EXISTS reference_entries");
        db.execSQL("DROP TABLE IF EXISTS shopping_items");
        db.execSQL("DROP TABLE IF EXISTS history");
        db.execSQL("DROP TABLE IF EXISTS tasks");
        db.execSQL("DROP TABLE IF EXISTS tabs");
        db.execSQL("DROP TABLE IF EXISTS sections");
        onCreate(db);
    }
}