package com.example.taskmgr.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.model.ShoppingItem;

import java.util.ArrayList;
import java.util.List;

public class ShoppingDao {
    private final DatabaseHelper dbHelper;

    public ShoppingDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(ShoppingItem s) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("tab_id", s.tabId);
        cv.put("title", s.title);
        if (s.price != null) cv.put("price", s.price);
        else cv.putNull("price");
        cv.put("is_bought", s.isBought ? 1 : 0);
        cv.put("is_selected", s.isSelected ? 1 : 0);
        cv.put("created_at", s.createdAt);
        if (s.category != null) cv.put("category", s.category);
        else cv.putNull("category");
        return db.insert("shopping_items", null, cv);
    }

    public List<String> getCategories(long tabId) {
        List<String> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT DISTINCT category FROM shopping_items " +
                "WHERE tab_id = ? AND category IS NOT NULL AND category <> '' " +
                "ORDER BY category COLLATE NOCASE ASC",
            new String[]{String.valueOf(tabId)})) {
            while (c.moveToNext()) {
                result.add(c.getString(0));
            }
        }
        return result;
    }

    public void setBought(long id, boolean bought) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_bought", bought ? 1 : 0);
        db.update("shopping_items", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public double getTotalPrice(long tabId, String category) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql;
        String[] args;
        if (category == null || category.trim().isEmpty()) {
            sql = "SELECT SUM(price) FROM shopping_items " +
                "WHERE tab_id = ? AND is_bought = 0";
            args = new String[]{String.valueOf(tabId)};
        } else {
            sql = "SELECT SUM(price) FROM shopping_items " +
                "WHERE tab_id = ? AND is_bought = 0 AND category = ?";
            args = new String[]{String.valueOf(tabId), category};
        }
        try (Cursor c = db.rawQuery(sql, args)) {
            if (c.moveToFirst()) {
                return c.isNull(0) ? 0 : c.getDouble(0);
            }
            return 0;
        }
    }

    public void update(ShoppingItem s) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", s.title);
        if (s.price != null) cv.put("price", s.price);
        else cv.putNull("price");
        cv.put("is_bought", s.isBought ? 1 : 0);
        cv.put("is_selected", s.isSelected ? 1 : 0);
        if (s.category != null) cv.put("category", s.category);
        else cv.putNull("category");
        db.update("shopping_items", cv, "id = ?", new String[]{String.valueOf(s.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("shopping_items", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<ShoppingItem> getByTabSorted(long tabId, String category,
                                             SortMode mode, boolean ascending) {
        List<ShoppingItem> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = buildSortedQuery(db, tabId, category, mode, ascending)) {
            while (c.moveToNext()) {
                ShoppingItem s = new ShoppingItem();
                s.id = c.getLong(c.getColumnIndexOrThrow("id"));
                s.tabId = c.getLong(c.getColumnIndexOrThrow("tab_id"));
                s.title = c.getString(c.getColumnIndexOrThrow("title"));
                int priceIdx = c.getColumnIndexOrThrow("price");
                s.price = c.isNull(priceIdx) ? null : c.getDouble(priceIdx);
                s.isBought = c.getInt(c.getColumnIndexOrThrow("is_bought")) == 1;
                s.isSelected = c.getInt(c.getColumnIndexOrThrow("is_selected")) == 1;
                s.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                s.category = c.getString(c.getColumnIndexOrThrow("category"));
                result.add(s);
            }
        }
        return result;
    }

    private Cursor buildSortedQuery(SQLiteDatabase db, long tabId, String category,
                                    SortMode mode, boolean ascending) {
        String where;
        String[] args;
        if (category == null || category.trim().isEmpty()) {
            where = "tab_id = ?";
            args = new String[]{String.valueOf(tabId)};
        } else {
            where = "tab_id = ? AND category = ?";
            args = new String[]{String.valueOf(tabId), category};
        }

        String dir = ascending ? "ASC" : "DESC";
        String key = switch (mode) {
            case PRICE -> "price";
            case TITLE -> "title";
            default -> "created_at";
        };
        String orderBy;
        if (mode == SortMode.PRICE) {
            orderBy = "is_bought ASC, CASE WHEN price IS NULL THEN 1 ELSE 0 END ASC, price " + dir;
        } else {
            orderBy = "is_bought ASC, " + key + " " + dir;
        }

        return db.query("shopping_items", null, where, args,
            null, null, orderBy);
    }

    public enum SortMode {
        CREATED,
        PRICE,
        TITLE
    }
}
