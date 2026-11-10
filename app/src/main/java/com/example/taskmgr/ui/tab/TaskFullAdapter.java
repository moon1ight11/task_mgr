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

    private final List<Task> items = new ArrayList<>();
    private final OnTaskClick clickListener;
    private final OnTaskLongClick longClickListener;
    private Tab currentTab;
    private long highlightedTaskId = -1;

    public TaskFullAdapter(OnTaskClick clickListener, OnTaskLongClick longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setCurrentTab(Tab tab) {
        this.currentTab = tab;
    }

    public void setItems(List<Task> newItems) {
        int oldSize = items.size();
        items.clear();
        if (newItems != null) items.addAll(newItems);
        int newSize = items.size();

        if (oldSize == newSize) {
            if (newSize > 0) notifyItemRangeChanged(0, newSize);
        } else {
            if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
            if (newSize > 0) notifyItemRangeInserted(0, newSize);
        }
    }

    public int positionOfTask(long taskId) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == taskId) return i;
        return -1;
    }

    public void setHighlightedTaskId(long taskId) {
        long oldId = highlightedTaskId;
        highlightedTaskId = taskId;
        int oldPos = positionOfTask(oldId);
        int newPos = positionOfTask(taskId);
        if (oldPos != -1) notifyItemChanged(oldPos);
        if (newPos != -1 && newPos != oldPos) notifyItemChanged(newPos);
    }

    public void clearHighlight() {
        if (highlightedTaskId == -1) return;
        int oldPos = positionOfTask(highlightedTaskId);
        highlightedTaskId = -1;
        if (oldPos != -1) notifyItemChanged(oldPos);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_task_full, parent, false);
        VH vh = new VH(v);
        TaskAdapter.InstallRoundedClip(vh.contentRoot, parent);
        return vh;
    }

    @SuppressWarnings("SetTextI18n")
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

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(t);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(t);
            return true;
        });

        if (h.stripe != null) {
            h.stripe.setBackgroundColor(urgencyColor(ctx, UrgencyCalculator.calculate(t, currentTab)));
        }
    }

    private int urgencyColor(Context ctx, UrgencyCalculator.Urgency u) {
        return switch (u) {
            case OVERDUE -> ContextCompat.getColor(ctx, R.color.semantic_danger);
            case SOON -> ContextCompat.getColor(ctx, R.color.semantic_warning);
            case NORMAL -> ContextCompat.getColor(ctx, R.color.semantic_neutral);
            default -> ContextCompat.getColor(ctx, R.color.semantic_neutral_weak);
        };
    }

    private int priorityColor(Context ctx, String p) {
        if (p == null) return ContextCompat.getColor(ctx, R.color.semantic_neutral_strong);
        return switch (p) {
            case "important" -> ContextCompat.getColor(ctx, R.color.semantic_info);
            case "whatever" -> ContextCompat.getColor(ctx, R.color.semantic_neutral);
            default -> ContextCompat.getColor(ctx, R.color.semantic_neutral_strong);
        };
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
        return switch (p) {
            case "important" -> "важно";
            case "whatever" -> "потом";
            default -> "обычно";
        };
    }

    private String dueLabel(Task t) {
        return switch (t.dueType) {
            case "exact_date" -> t.dueDate != null ? "срок: " + fmt(t.dueDate) : "срок: —";
            case "range" -> "период: " + formatRange(t.dueFrom, t.dueTo);
            case "by_km" -> t.dueKm != null ? "на " + t.dueKm + " км" : "по км";
            case "after_last" -> "после выполнения";
            default -> "";
        };
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

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnTaskClick {
        void onClick(Task t);
    }

    public interface OnTaskLongClick {
        void onLongClick(Task t);
    }

    public static class VH extends RecyclerView.ViewHolder {
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
