package com.example.taskmgr.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "taskmgr.db";
    private static final int DATABASE_VERSION = 9;
    private static volatile DatabaseHelper instance;

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static DatabaseHelper getInstance(Context context) {
        DatabaseHelper local = instance;
        if (local == null) {
            synchronized (DatabaseHelper.class) {
                local = instance;
                if (local == null) {
                    local = new DatabaseHelper(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    public void runInTransaction(java.util.function.Consumer<SQLiteDatabase> work) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            work.accept(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sections (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, " +
            "icon TEXT, " +
            "is_private INTEGER DEFAULT 0, " +
            "sort_order INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE tabs (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "section_id INTEGER NOT NULL, " +
            "name TEXT NOT NULL, " +
            "current_km INTEGER, " +
            "year INTEGER, " +
            "sort_order INTEGER DEFAULT 0, " +
            "show_tasks INTEGER DEFAULT 1, " +
            "show_shopping INTEGER DEFAULT 1, " +
            "show_reference INTEGER DEFAULT 1, " +
            "FOREIGN KEY(section_id) REFERENCES sections(id) ON DELETE CASCADE)");

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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_tab_status ON tasks(tab_id, status)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_status_priority ON tasks(status, priority)");

        db.execSQL("CREATE TABLE history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "task_id INTEGER NOT NULL, " +
            "completed_at INTEGER NOT NULL, " +
            "km_at_completion INTEGER, " +
            "note TEXT, " +
            "FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_task ON history(task_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_completed ON history(completed_at)");

        db.execSQL("CREATE TABLE shopping_items (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "tab_id INTEGER NOT NULL, " +
            "title TEXT NOT NULL, " +
            "price REAL, " +
            "is_bought INTEGER DEFAULT 0, " +
            "is_selected INTEGER DEFAULT 0, " +
            "created_at INTEGER NOT NULL, " +
            "category TEXT, " +
            "FOREIGN KEY(tab_id) REFERENCES tabs(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_shopping_tab ON shopping_items(tab_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_shopping_tab_bought ON shopping_items(tab_id, is_bought)");

        db.execSQL("CREATE TABLE reference_entries (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "tab_id INTEGER NOT NULL, " +
            "title TEXT NOT NULL, " +
            "value TEXT NOT NULL, " +
            "category TEXT, " +
            "description TEXT, " +
            "FOREIGN KEY(tab_id) REFERENCES tabs(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reference_tab ON reference_entries(tab_id)");

        db.execSQL("CREATE TABLE quick_notes (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "body TEXT NOT NULL, " +
            "created_at INTEGER NOT NULL, " +
            "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_created ON quick_notes(created_at DESC)");

        db.execSQL("CREATE TABLE subscriptions (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, " +
            "amount REAL NOT NULL, " +
            "charge_day INTEGER NOT NULL, " +
            "next_charge_at INTEGER NOT NULL, " +
            "description TEXT, " +
            "is_active INTEGER DEFAULT 1, " +
            "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_subs_next ON subscriptions(is_active, next_charge_at)");

        db.execSQL("CREATE TABLE utility_meters (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, " +
            "unit TEXT NOT NULL, " +
            "rate REAL NOT NULL, " +
            "is_active INTEGER DEFAULT 1, " +
            "sort_order INTEGER DEFAULT 0, " +
            "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE utility_periods (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "period_year INTEGER NOT NULL, " +
            "period_month INTEGER NOT NULL, " +
            "house TEXT NOT NULL, " +
            "note TEXT, " +
            "paid INTEGER DEFAULT 0, " +
            "paid_at INTEGER, " +
            "created_at INTEGER NOT NULL, " +
            "UNIQUE(period_year, period_month, house))");

        db.execSQL("CREATE TABLE utility_readings (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "period_id INTEGER NOT NULL, " +
            "meter_id INTEGER NOT NULL, " +
            "previous_value REAL NOT NULL, " +
            "current_value REAL NOT NULL, " +
            "consumption REAL NOT NULL, " +
            "rate_applied REAL NOT NULL, " +
            "amount REAL NOT NULL, " +
            "created_at INTEGER NOT NULL, " +
            "UNIQUE(period_id, meter_id), " +
            "FOREIGN KEY(period_id) REFERENCES utility_periods(id) ON DELETE CASCADE, " +
            "FOREIGN KEY(meter_id) REFERENCES utility_meters(id) ON DELETE CASCADE)");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_utility_readings_period ON utility_readings(period_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_utility_periods_ym ON utility_periods(period_year, period_month)");

        db.execSQL("CREATE TABLE app_settings (" +
            "`key` TEXT PRIMARY KEY, " +
            "value TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sections ADD COLUMN is_private INTEGER DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (" +
                "`key` TEXT PRIMARY KEY, value TEXT)");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE shopping_items ADD COLUMN category TEXT");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE tabs ADD COLUMN show_tasks INTEGER DEFAULT 1");
            db.execSQL("ALTER TABLE tabs ADD COLUMN show_shopping INTEGER DEFAULT 1");
            db.execSQL("ALTER TABLE tabs ADD COLUMN show_reference INTEGER DEFAULT 1");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE reference_entries ADD COLUMN description TEXT");
        }
        if (oldVersion < 6) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_tab_status ON tasks(tab_id, status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_status_priority ON tasks(status, priority)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_task ON history(task_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_completed ON history(completed_at)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_shopping_tab ON shopping_items(tab_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_shopping_tab_bought ON shopping_items(tab_id, is_bought)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reference_tab ON reference_entries(tab_id)");
        }
        if (oldVersion < 7) {
            db.execSQL("CREATE TABLE IF NOT EXISTS quick_notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "body TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_created ON quick_notes(created_at DESC)");
        }
        if (oldVersion < 8) {
            db.execSQL("CREATE TABLE IF NOT EXISTS subscriptions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "amount REAL NOT NULL, " +
                "charge_day INTEGER NOT NULL, " +
                "next_charge_at INTEGER NOT NULL, " +
                "description TEXT, " +
                "is_active INTEGER DEFAULT 1, " +
                "created_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_subs_next ON subscriptions(is_active, next_charge_at)");
        }
        if (oldVersion < 9) {
            db.execSQL("CREATE TABLE IF NOT EXISTS utility_meters (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "rate REAL NOT NULL, " +
                "is_active INTEGER DEFAULT 1, " +
                "sort_order INTEGER DEFAULT 0, " +
                "created_at INTEGER NOT NULL)");

            db.execSQL("CREATE TABLE IF NOT EXISTS utility_periods (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "period_year INTEGER NOT NULL, " +
                "period_month INTEGER NOT NULL, " +
                "house TEXT NOT NULL, " +
                "note TEXT, " +
                "paid INTEGER DEFAULT 0, " +
                "paid_at INTEGER, " +
                "created_at INTEGER NOT NULL, " +
                "UNIQUE(period_year, period_month, house))");

            db.execSQL("CREATE TABLE IF NOT EXISTS utility_readings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "period_id INTEGER NOT NULL, " +
                "meter_id INTEGER NOT NULL, " +
                "previous_value REAL NOT NULL, " +
                "current_value REAL NOT NULL, " +
                "consumption REAL NOT NULL, " +
                "rate_applied REAL NOT NULL, " +
                "amount REAL NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "UNIQUE(period_id, meter_id), " +
                "FOREIGN KEY(period_id) REFERENCES utility_periods(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(meter_id) REFERENCES utility_meters(id) ON DELETE CASCADE)");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_utility_readings_period ON utility_readings(period_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_utility_periods_ym ON utility_periods(period_year, period_month)");
        }
    }
}
