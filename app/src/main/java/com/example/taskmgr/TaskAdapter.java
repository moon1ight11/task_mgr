package com.example.taskmgr;

import android.graphics.Color;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.model.Section;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.service.TaskUrgencyComparator;
import com.example.taskmgr.service.UrgencyCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {

    public interface OnTaskClick { void onClick(Task task); }
    public interface OnTaskComplete { void onComplete(Task task); }

    private final List<Task> items = new ArrayList<>();
    private final Map<Long, Tab> tabsById = new HashMap<>();
    private final Map<Long, String> sectionNamesById = new HashMap<>();
    private final OnTaskClick clickListener;
    private final OnTaskComplete completeListener;

    private long highlightedTaskId = -1;

    public TaskAdapter(OnTaskClick clickListener, OnTaskComplete completeListener) {
        this.clickListener = clickListener;
        this.completeListener = completeListener;
    }

    public void setItems(List<Task> newItems, List<Tab> allTabs, List<Section> allSections) {
        tabsById.clear();
        if (allTabs != null) {
            for (Tab t : allTabs) tabsById.put(t.id, t);
        }
        sectionNamesById.clear();
        if (allSections != null) {
            for (Section s : allSections) sectionNamesById.put(s.id, s.name);
        }
        items.clear();
        if (newItems != null) items.addAll(newItems);
        items.sort(new TaskUrgencyComparator(tabsById));
        notifyDataSetChanged();
    }

    public int positionOfTask(long taskId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == taskId) return i;
        }
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
                .inflate(R.layout.item_task, parent, false);
        VH vh = new VH(v);
        installRoundedClip(vh.contentRoot, parent);
        return vh;
    }

    static void installRoundedClip(View contentRoot, ViewGroup parent) {
        if (contentRoot == null) return;
        final float radius = parent.getResources().getDimension(R.dimen.card_corner_radius);
        contentRoot.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        contentRoot.setClipToOutline(true);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Task t = items.get(position);
        Tab tab = tabsById.get(t.tabId);

        holder.title.setText(t.title);

        String origin = buildOrigin(t, tab);
        if (origin != null) {
            holder.origin.setText(origin);
            holder.origin.setVisibility(View.VISIBLE);
        } else {
            holder.origin.setVisibility(View.GONE);
        }

        holder.subtitle.setText(buildSubtitle(t));

        if (t.id == highlightedTaskId) {
            holder.itemView.setBackgroundColor(0x4400FFB2);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(t);
        });

        holder.btnComplete.setOnClickListener(v -> {
            if (completeListener != null) completeListener.onComplete(t);
        });

        UrgencyCalculator.Urgency urgency = UrgencyCalculator.calculate(t, tab);
        int stripeColor;
        switch (urgency) {
            case OVERDUE: stripeColor = 0xFFFF3B6B; break;
            case SOON:    stripeColor = 0xFFFBBF24; break;
            case NORMAL:  stripeColor = 0xFF00FFB2; break;
            default:      stripeColor = 0xFF64748B; break;
        }
        if (holder.stripe != null) holder.stripe.setBackgroundColor(stripeColor);
    }

    private String buildOrigin(Task t, Tab tab) {
        String section = (tab != null)
                ? sectionNamesById.get(tab.sectionId)
                : null;
        String tabName = (tab != null) ? tab.name : null;

        if (TextUtils.isEmpty(section) && TextUtils.isEmpty(tabName)) return null;
        if (TextUtils.isEmpty(section)) return tabName;
        if (TextUtils.isEmpty(tabName)) return section;
        return section + " • " + tabName;
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
                return formatRange(t.dueFrom, t.dueTo);
            case "by_km":
                return t.dueKm != null ? t.dueKm + " км" : "—";
            case "after_last":
                return "после выполнения";
            default:
                return "—";
        }
    }

    private String formatRange(Long from, Long to) {
        if (from != null && to != null) return formatDate(from) + " — " + formatDate(to);
        if (from != null) return "с " + formatDate(from);
        if (to != null) return "до " + formatDate(to);
        return "—";
    }

    private String formatDate(long millis) {
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(millis));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, origin, subtitle;
        ImageButton btnComplete;
        View stripe, contentRoot;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.task_title);
            origin = itemView.findViewById(R.id.task_origin);
            subtitle = itemView.findViewById(R.id.task_subtitle);
            btnComplete = itemView.findViewById(R.id.btn_complete);
            stripe = itemView.findViewById(R.id.urgency_stripe);
            contentRoot = itemView.findViewById(R.id.task_content_root);
        }
    }
}