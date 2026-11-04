package com.example.taskmgr;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {

    public interface OnTaskClick {
        void onClick(Task task);
    }

    private final List<Task> items = new ArrayList<>();
    private final OnTaskClick clickListener;

    public TaskAdapter(OnTaskClick clickListener) {
        this.clickListener = clickListener;
    }

    public void setItems(List<Task> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Task t = items.get(position);
        holder.title.setText(t.title);
        holder.subtitle.setText(buildSubtitle(t));
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(t);
        });
    }

    private String buildSubtitle(Task t) {
        StringBuilder sb = new StringBuilder();
        sb.append("Приоритет: ").append(priorityLabel(t.priority));
        if (t.dueType != null && !t.dueType.equals("none")) {
            sb.append(" • Срок: ").append(dueLabel(t));
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
                return t.dueDate != null ? formatDate(t.dueDate) : "—";
            case "range":
                return "период";
            case "by_km":
                return t.dueKm != null ? t.dueKm + " км" : "—";
            case "after_last":
                return "после выполнения";
            default:
                return "—";
        }
    }

    private String formatDate(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(millis));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, subtitle;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.task_title);
            subtitle = itemView.findViewById(R.id.task_subtitle);
        }
    }
}