package com.example.taskmgr.service;

import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;

import java.util.Comparator;
import java.util.Map;

/**
 * Сортировка: OVERDUE → SOON → NORMAL → NONE, внутри — по расстоянию до дедлайна;
 * Без срока — в конце;
 */
public class TaskUrgencyComparator implements Comparator<Task> {

    private static final long KM_UNIT = 1_000_000L; // чтобы км не смешивались с мс

    private final Map<Long, Tab> tabsById;

    public TaskUrgencyComparator(Map<Long, Tab> tabsById) {
        this.tabsById = tabsById;
    }

    @Override
    public int compare(Task a, Task b) {
        Tab ta = tabsById.get(a.tabId);
        Tab tb = tabsById.get(b.tabId);

        UrgencyCalculator.Urgency ua = UrgencyCalculator.calculate(a, ta);
        UrgencyCalculator.Urgency ub = UrgencyCalculator.calculate(b, tb);

        int ra = rank(ua), rb = rank(ub);
        if (ra != rb) return Integer.compare(rb, ra); // OVERDUE(3) первым

        long da = distance(a, ta);
        long db = distance(b, tb);
        if (da != db) return Long.compare(da, db);

        // fallback — новые выше
        return Long.compare(b.createdAt, a.createdAt);
    }

    private static int rank(UrgencyCalculator.Urgency u) {
        switch (u) {
            case OVERDUE: return 3;
            case SOON:    return 2;
            case NORMAL:  return 1;
            default:      return 0;
        }
    }

    private static long distance(Task t, Tab tab) {
        long now = System.currentTimeMillis();
        if (t.dueType == null || t.dueType.equals("none")) return Long.MAX_VALUE;

        switch (t.dueType) {
            case "exact_date":
                return t.dueDate != null ? Math.abs(t.dueDate - now) : Long.MAX_VALUE;

            case "range":
                if (t.dueTo != null) return Math.abs(t.dueTo - now);
                if (t.dueFrom != null) return Math.abs(t.dueFrom - now);
                return Long.MAX_VALUE;

            case "by_km":
                if (t.dueKm != null && tab != null && tab.currentKm != null) {
                    long remaining = t.dueKm - tab.currentKm;
                    return Math.max(0, remaining) * KM_UNIT;
                }
                return Long.MAX_VALUE;

            default:
                return Long.MAX_VALUE;
        }
    }
}