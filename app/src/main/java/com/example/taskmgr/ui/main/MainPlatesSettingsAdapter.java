package com.example.taskmgr.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.MainPlate;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class MainPlatesSettingsAdapter
    extends RecyclerView.Adapter<MainPlatesSettingsAdapter.VH> {

    public interface OnVisibleChanged { void onChange(MainPlate p, boolean visible); }
    public interface OnMove { void onMove(int position); }

    private final List<MainPlate> items = new ArrayList<>();
    private final OnVisibleChanged onVisibleChanged;
    private final OnMove onMoveUp;
    private final OnMove onMoveDown;

    public MainPlatesSettingsAdapter(OnVisibleChanged v, OnMove up, OnMove down) {
        this.onVisibleChanged = v;
        this.onMoveUp = up;
        this.onMoveDown = down;
    }

    public void setItems(List<MainPlate> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
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

        h.btnUp.setEnabled(position > 0);
        h.btnDown.setEnabled(position < items.size() - 1);

        h.btnUp.setOnClickListener(v -> {
            if (onMoveUp != null) onMoveUp.onMove(position);
        });
        h.btnDown.setOnClickListener(v -> {
            if (onMoveDown != null) onMoveDown.onMove(position);
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
        ImageButton btnUp, btnDown;

        public VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.setting_icon);
            label = itemView.findViewById(R.id.setting_label);
            sw = itemView.findViewById(R.id.setting_switch);
            btnUp = itemView.findViewById(R.id.setting_up);
            btnDown = itemView.findViewById(R.id.setting_down);
        }
    }
}
