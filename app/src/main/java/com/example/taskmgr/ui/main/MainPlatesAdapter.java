package com.example.taskmgr.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.MainPlate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainPlatesAdapter extends RecyclerView.Adapter<MainPlatesAdapter.VH> {

    public interface OnPlateClick {
        void onClick(MainPlate plate);
    }

    private final List<MainPlate> items = new ArrayList<>();
    private final Map<String, Integer> badgeByKey = new HashMap<>();
    private final OnPlateClick click;

    public MainPlatesAdapter(OnPlateClick click) {
        this.click = click;
    }

    public void setItems(List<MainPlate> plates, Map<String, Integer> badges) {
        items.clear();
        if (plates != null) {
            for (MainPlate p : plates) if (p.isVisible) items.add(p);
        }
        badgeByKey.clear();
        if (badges != null) badgeByKey.putAll(badges);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_main_plate, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MainPlate p = items.get(position);
        h.label.setText(p.label);
        h.icon.setImageResource(p.iconRes);

        if (p.tintGreen) {
            h.icon.setImageTintList(
                ContextCompat.getColorStateList(h.itemView.getContext(), R.color.neon_green));
        } else {
            h.icon.setImageTintList(null);
        }

        if ("private".equals(p.key)) {
            h.root.setBackgroundResource(R.drawable.bg_plate_private);
        } else if ("stats".equals(p.key)) {
            h.root.setBackgroundResource(R.drawable.bg_plate_stats);
        } else {
            h.root.setBackgroundResource(R.drawable.bg_plate);
        }

        Integer count = badgeByKey.get(p.key);
        if (p.showBadge && count != null && count > 0) {
            h.badge.setText(count > 99 ? "99+" : String.valueOf(count));
            h.badge.setVisibility(View.VISIBLE);
        } else {
            h.badge.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onClick(p);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView label;
        TextView badge;
        LinearLayout root;

        public VH(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.plate_root);
            icon = itemView.findViewById(R.id.plate_icon);
            label = itemView.findViewById(R.id.plate_label);
            badge = itemView.findViewById(R.id.plate_badge);
        }
    }
}
