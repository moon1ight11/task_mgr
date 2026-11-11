package com.example.taskmgr.service;

import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;

import java.util.Comparator;
import java.util.Map;

public class TaskUrgencyComparator implements Comparator<Task> {

    private static final long KM_UNIT = 1_000_000L; // чтобы км не смешивались с мс

    private final Map<Long, Tab> tabsById;

    public TaskUrgencyComparator(Map<Long, Tab> tabsById) {
        this.tabsById = tabsById;
    }

    private static int rank(UrgencyCalculator.Urgency u) {
        return switch (u) {
            case OVERDUE -> 3;
            case SOON -> 2;
            case NORMAL -> 1;
            default -> 0;
        };
    }

    private static long distance(Task t, Tab tab) {
        long now = System.currentTimeMillis();
        if (t.dueType == null || t.dueType.equals("none")) return Long.MAX_VALUE;

        return switch (t.dueType) {
            case "exact_date" -> t.dueDate != null ? Math.abs(t.dueDate - now) : Long.MAX_VALUE;

            case "range" -> {
                if (t.dueTo != null) yield Math.abs(t.dueTo - now);
                if (t.dueFrom != null) yield Math.abs(t.dueFrom - now);
                yield Long.MAX_VALUE;
            }

            case "by_km" -> {
                if (t.dueKm != null && tab != null && tab.currentKm != null) {
                    long remaining = t.dueKm - tab.currentKm;
                    yield Math.max(0, remaining) * KM_UNIT;
                }
                yield Long.MAX_VALUE;
            }

            default -> Long.MAX_VALUE;
        };
    }

    @Override
    public int compare(Task a, Task b) {
        Tab ta = tabsById.get(a.tabId);
        Tab tb = tabsById.get(b.tabId);

        UrgencyCalculator.Urgency ua = UrgencyCalculator.calculate(a, ta);
        UrgencyCalculator.Urgency ub = UrgencyCalculator.calculate(b, tb);

        int ra = rank(ua), rb = rank(ub);
        if (ra != rb) return Integer.compare(rb, ra);

        long da = distance(a, ta);
        long db = distance(b, tb);
        if (da != db) return Long.compare(da, db);

        return Long.compare(a.createdAt, b.createdAt);
    }
}
