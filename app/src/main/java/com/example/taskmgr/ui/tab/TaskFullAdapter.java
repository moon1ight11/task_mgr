package com.example.taskmgr.ui.tab;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.service.UrgencyCalculator;
import com.example.taskmgr.ui.main.TaskAdapter;

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
    private long highlightedTaskId = -1;

    public void setCurrentTab(Tab tab) { this.currentTab = tab; }

    public TaskFullAdapter(OnTaskClick clickListener, OnTaskLongClick longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setItems(List<Task> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public int positionOfTask(long taskId) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == taskId) return i;
        return -1;
    }

    public void setHighlightedTaskId(long taskId) {
        highlightedTaskId = taskId;
        notifyDataSetChanged();
    }

    public void clearHighlight() {
        if (highlightedTaskId == -1) return;
        highlightedTaskId = -1;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_full, parent, false);
        VH vh = new VH(v);
        TaskAdapter.InstallRoundedClip(vh.contentRoot, parent);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Context ctx = h.itemView.getContext();
        Task t = items.get(position);
        h.title.setText(t.title);
        if (t.description != null && !t.description.isEmpty()) {
            h.description.setText(t.description);
            h.description.setVisibility(View.VISIBLE);
        } else {
            h.description.setVisibility(View.GONE);
        }
        h.meta.setText(buildMetaSpan(ctx, t, currentTab));
        h.created.setText("Создано: " + fmtDateTime(t.createdAt));

        if (t.id == highlightedTaskId) {
            h.inner.setBackgroundColor(0x4400FFB2);
        } else {
            h.inner.setBackgroundColor(Color.TRANSPARENT);
        }

        h.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onClick(t); });
        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(t);
            return true;
        });

        if (h.stripe != null) {
            h.stripe.setBackgroundColor(urgencyColor(ctx, UrgencyCalculator.calculate(t, currentTab)));
        }
    }

    private int urgencyColor(Context ctx, UrgencyCalculator.Urgency u) {
        switch (u) {
            case OVERDUE: return ContextCompat.getColor(ctx, R.color.semantic_danger);
            case SOON:    return ContextCompat.getColor(ctx, R.color.semantic_warning);
            case NORMAL:  return ContextCompat.getColor(ctx, R.color.semantic_neutral);
            default:      return ContextCompat.getColor(ctx, R.color.semantic_neutral_weak);
        }
    }

    private int priorityColor(Context ctx, String p) {
        if (p == null) return ContextCompat.getColor(ctx, R.color.semantic_neutral_strong);
        switch (p) {
            case "important": return ContextCompat.getColor(ctx, R.color.semantic_info);
            case "whatever":  return ContextCompat.getColor(ctx, R.color.semantic_neutral);
            default:          return ContextCompat.getColor(ctx, R.color.semantic_neutral_strong);
        }
    }

    private CharSequence buildMetaSpan(Context ctx, Task t, Tab tab) {
        String pLabel = priorityLabel(t.priority);
        String dueText = (t.dueType != null && !t.dueType.equals("none")) ? dueLabel(t) : null;
        boolean hasRepeat = (t.repeatRule != null && !t.repeatRule.equals("none"));

        String prefixP = "Приоритет: ";
        String prefixD = " • ";
        String suffixRepeat = " • повтор";

        StringBuilder full = new StringBuilder();
        full.append(prefixP).append(pLabel);
        int pStart = prefixP.length();
        int pEnd = pStart + pLabel.length();
        int dStart = -1, dEnd = -1;
        if (dueText != null) {
            full.append(prefixD).append(dueText);
            dStart = full.length() - dueText.length();
            dEnd = full.length();
        }
        if (hasRepeat) full.append(suffixRepeat);

        SpannableString sp = new SpannableString(full.toString());
        int neutral = ContextCompat.getColor(ctx, R.color.semantic_neutral_strong);

        sp.setSpan(new ForegroundColorSpan(neutral), 0, full.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new ForegroundColorSpan(priorityColor(ctx, t.priority)),
                pStart, pEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (dStart >= 0) {
            sp.setSpan(new ForegroundColorSpan(urgencyColor(ctx, UrgencyCalculator.calculate(t, tab))),
                    dStart, dEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sp;
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
        View stripe, contentRoot, inner;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.task_title);
            description = v.findViewById(R.id.task_description);
            meta = v.findViewById(R.id.task_meta);
            created = v.findViewById(R.id.task_created);
            stripe = v.findViewById(R.id.urgency_stripe);
            contentRoot = v.findViewById(R.id.task_content_root);
            inner = v.findViewById(R.id.task_inner);
        }
    }
}