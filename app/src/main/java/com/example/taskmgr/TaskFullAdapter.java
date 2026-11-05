package com.example.taskmgr;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.service.UrgencyCalculator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskFullAdapter extends RecyclerView.Adapter<TaskFullAdapter.VH> {

    public interface OnTaskClick { void onClick(Task t); }
    public interface OnTaskLongClick { void onLongClick(Task t); }

    private final List<Task> items = new ArrayList<>();
    private final OnTaskClick clickListener;
    private final OnTaskLongClick longClickListener;

    private Tab currentTab;

    public void setCurrentTab(Tab tab) {
        this.currentTab = tab;
    }

    public TaskFullAdapter(OnTaskClick clickListener, OnTaskLongClick longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setItems(List<Task> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_full, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Task t = items.get(position);
        h.title.setText(t.title);
        if (t.description != null && !t.description.isEmpty()) {
            h.description.setText(t.description);
            h.description.setVisibility(View.VISIBLE);
        } else {
            h.description.setVisibility(View.GONE);
        }
        h.meta.setText(buildMeta(t));

        h.created.setText("Создано: " + fmtDateTime(t.createdAt));

        UrgencyCalculator.Urgency urgency = UrgencyCalculator.calculate(t, currentTab);
        int color;
        switch (urgency) {
            case OVERDUE: color = 0xFFD32F2F; break;
            case SOON:    color = 0xFFFF9800; break;
            case NORMAL:  color = 0xFF4CAF50; break;
            default:      color = 0xFF9E9E9E; break;
        }
        h.meta.setTextColor(color);

        h.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onClick(t); });
        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(t);
            return true;
        });
    }

    private String buildMeta(Task t) {
        StringBuilder sb = new StringBuilder();
        sb.append("Приоритет: ").append(priorityLabel(t.priority));
        if (t.dueType != null && !t.dueType.equals("none")) {
            sb.append(" • ").append(dueLabel(t));
        }
        if (t.repeatRule != null && !t.repeatRule.equals("none")) {
            sb.append(" • повтор");
        }
        return sb.toString();
    }

    private String priorityLabel(String p) {
        if (p == null) return "—";
        switch (p) {
            case "important": return "важно";
            case "whatever": return "потом";
            default: return "обычно";
        }
    }

    private String dueLabel(Task t) {
        switch (t.dueType) {
            case "exact_date":
                return t.dueDate != null ? "срок: " + fmt(t.dueDate) : "срок: —";
            case "range":
                return "период: " + formatRange(t.dueFrom, t.dueTo);
            case "by_km":
                return t.dueKm != null ? "на " + t.dueKm + " км" : "по км";
            case "after_last":
                return "после выполнения";
            default:
                return "";
        }
    }

    private String formatRange(Long from, Long to) {
        if (from != null && to != null) return fmt(from) + " — " + fmt(to);
        if (from != null) return "с " + fmt(from);
        if (to != null) return "до " + fmt(to);
        return "—";
    }

    private String fmt(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date(millis));
    }

    private String fmtDateTime(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, description, meta, created;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.task_title);
            description = v.findViewById(R.id.task_description);
            meta = v.findViewById(R.id.task_meta);
            created = v.findViewById(R.id.task_created);
        }
    }
}