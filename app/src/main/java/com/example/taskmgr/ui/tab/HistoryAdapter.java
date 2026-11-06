package com.example.taskmgr.ui.tab;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.HistoryEntry;
import com.example.taskmgr.model.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private final List<HistoryEntry> items = new ArrayList<>();
    private final Map<Long, Task> tasksById = new HashMap<>();

    public void setItems(List<HistoryEntry> newItems, List<Task> allTasks) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        tasksById.clear();
        if (allTasks != null) {
            for (Task t : allTasks) tasksById.put(t.id, t);
        }
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HistoryEntry e = items.get(position);
        Task task = tasksById.get(e.taskId);
        h.title.setText(task != null ? task.title : "Задача #" + e.taskId);

        StringBuilder sb = new StringBuilder();
        sb.append("Выполнено: ").append(fmtDateTime(e.completedAt));
        if (e.kmAtCompletion != null) sb.append(" • ").append(e.kmAtCompletion).append(" км");
        if (e.note != null && !e.note.isEmpty()) sb.append(" • ").append(e.note);
        h.meta.setText(sb.toString());

        if (task != null && task.createdAt > 0) {
            h.created.setText("Создано: " + fmtDateTime(task.createdAt));
            h.created.setVisibility(View.VISIBLE);
        } else {
            h.created.setVisibility(View.GONE);
        }
    }

    private String fmtDateTime(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(new Date(millis));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, meta, created;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.history_title);
            meta = v.findViewById(R.id.history_meta);
            created = v.findViewById(R.id.history_created);
        }
    }
}