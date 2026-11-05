package com.example.taskmgr.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.taskmgr.DatabaseHelper;
import com.example.taskmgr.model.ShoppingItem;

import java.util.ArrayList;
import java.util.List;

public class ShoppingDao {
    private final DatabaseHelper dbHelper;

    public ShoppingDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
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
        return db.insert("shopping_items", null, cv);
    }

    public List<ShoppingItem> getByTab(long tabId) {
        List<ShoppingItem> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("shopping_items", null, "tab_id = ?",
                    new String[]{String.valueOf(tabId)},
                    null, null, "created_at DESC");
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
                result.add(s);
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public void setBought(long id, boolean bought) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_bought", bought ? 1 : 0);
        db.update("shopping_items", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public void setSelected(long id, boolean selected) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_selected", selected ? 1 : 0);
        db.update("shopping_items", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    /** Сумма всех некупленных позиций вкладки. */
    public double getTotalPrice(long tabId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            String sql = "SELECT SUM(price) FROM shopping_items " +
                    "WHERE tab_id = ? AND is_bought = 0";
            c = db.rawQuery(sql, new String[]{String.valueOf(tabId)});
            if (c.moveToFirst()) {
                return c.isNull(0) ? 0 : c.getDouble(0);
            }
            return 0;
        } finally {
            if (c != null) c.close();
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
        db.update("shopping_items", cv, "id = ?", new String[]{String.valueOf(s.id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("shopping_items", "id = ?", new String[]{String.valueOf(id)});
    }
}