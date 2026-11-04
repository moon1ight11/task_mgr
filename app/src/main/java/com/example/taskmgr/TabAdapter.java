package com.example.taskmgr;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.model.Tab;

import java.util.ArrayList;
import java.util.List;

public class TabAdapter extends RecyclerView.Adapter<TabAdapter.VH> {

    public interface OnTabClick {
        void onClick(Tab tab);
    }

    private final List<Tab> items = new ArrayList<>();
    private final OnTabClick clickListener;
    private final boolean showSubtitle;

    public TabAdapter(boolean showSubtitle, OnTabClick clickListener) {
        this.showSubtitle = showSubtitle;
        this.clickListener = clickListener;
    }

    public void setItems(List<Tab> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tab, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Tab t = items.get(position);
        holder.name.setText(t.name);

        if (showSubtitle && (t.year != null || t.currentKm != null)) {
            StringBuilder sb = new StringBuilder();
            if (t.year != null) sb.append("Год: ").append(t.year);
            if (t.currentKm != null) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append("Пробег: ").append(t.currentKm).append(" км");
            }
            holder.subtitle.setText(sb.toString());
            holder.subtitle.setVisibility(View.VISIBLE);
        } else {
            holder.subtitle.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(t);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, subtitle;
        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tab_name);
            subtitle = itemView.findViewById(R.id.tab_subtitle);
        }
    }
}