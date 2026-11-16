package com.example.taskmgr.ui.main;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.MainPlate;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainPlatesSettingsAdapter
    extends RecyclerView.Adapter<MainPlatesSettingsAdapter.VH> {

    public interface OnVisibleChanged { void onChange(MainPlate p, boolean visible); }
    public interface OnStartDrag { void onStartDrag(RecyclerView.ViewHolder holder); }

    private final List<MainPlate> items = new ArrayList<>();
    private final OnVisibleChanged onVisibleChanged;
    private final OnStartDrag onStartDrag;

    public MainPlatesSettingsAdapter(OnVisibleChanged v, OnStartDrag startDrag) {
        this.onVisibleChanged = v;
        this.onStartDrag = startDrag;
    }

    public void setItems(List<MainPlate> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public List<MainPlate> getItems() {
        return items;
    }

    /** Меняет элементы местами в локальном списке (без похода в БД). */
    public void moveItem(int from, int to) {
        if (from < to) {
            for (int i = from; i < to; i++) Collections.swap(items, i, i + 1);
        } else {
            for (int i = from; i > to; i--) Collections.swap(items, i, i - 1);
        }
        notifyItemMoved(from, to);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_plate_setting, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MainPlate p = items.get(position);
        h.label.setText(p.label);
        h.icon.setImageResource(p.iconRes);

        if (p.tintGreen) {
            h.icon.setImageTintList(ContextCompat.getColorStateList(
                h.itemView.getContext(), R.color.neon_green));
        } else {
            h.icon.setImageTintList(null);
        }

        h.sw.setOnCheckedChangeListener(null);
        h.sw.setChecked(p.isVisible);
        h.sw.setOnCheckedChangeListener((b, checked) -> {
            if (onVisibleChanged != null) onVisibleChanged.onChange(p, checked);
        });

        h.drag.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && onStartDrag != null) {
                onStartDrag.onStartDrag(h);
            }
            return false; // пусть ItemTouchHelper сам обрабатывает касание
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView label;
        MaterialSwitch sw;
        ImageView drag;

        public VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.setting_icon);
            label = itemView.findViewById(R.id.setting_label);
            sw = itemView.findViewById(R.id.setting_switch);
            drag = itemView.findViewById(R.id.setting_drag);
        }
    }
}
