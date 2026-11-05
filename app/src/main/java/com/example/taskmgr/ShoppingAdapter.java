package com.example.taskmgr;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.model.ShoppingItem;

import java.util.ArrayList;
import java.util.List;

public class ShoppingAdapter extends RecyclerView.Adapter<ShoppingAdapter.VH> {

    public interface Listener {
        void onBoughtChanged(ShoppingItem item, boolean bought);
    }

    public interface OnShoppingLongClick {
        void onLongClick(ShoppingItem item);
    }

    private final List<ShoppingItem> items = new ArrayList<>();
    private final Listener listener;
    private final OnShoppingLongClick longClickListener;

    public ShoppingAdapter(Listener listener, OnShoppingLongClick longClickListener) {
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setItems(List<ShoppingItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shopping, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ShoppingItem s = items.get(position);

        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(s);
            return true;
        });

        h.title.setText(s.title);
        applyStrike(h.title, s.isBought);

        if (s.price != null) {
            h.price.setText(String.format("%.2f", s.price));
            h.price.setVisibility(View.VISIBLE);
        } else {
            h.price.setVisibility(View.GONE);
        }

        h.bought.setOnCheckedChangeListener(null);
        h.bought.setChecked(s.isBought);
        h.bought.setOnCheckedChangeListener((v, checked) -> {
            s.isBought = checked;
            applyStrike(h.title, checked);
            if (listener != null) listener.onBoughtChanged(s, checked);
        });
    }

    private void applyStrike(TextView tv, boolean strike) {
        int flags = tv.getPaintFlags();
        if (strike) {
            tv.setPaintFlags(flags | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            tv.setPaintFlags(flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, price;
        CheckBox bought;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.shopping_title);
            price = v.findViewById(R.id.shopping_price);
            bought = v.findViewById(R.id.check_bought);
        }
    }
}