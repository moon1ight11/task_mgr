package com.example.taskmgr.service;

import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.util.DateUtils;

public class UrgencyCalculator {

    private static final int SOON_DAYS = 3;
    private static final int SOON_KM = 500;

    public static Urgency calculate(Task t, Tab tab) {
        long now = System.currentTimeMillis();
        long startOfToday = DateUtils.startOfDay(now);
        long soonThreshold = now + (long) SOON_DAYS * 24 * 60 * 60 * 1000;

        if (t.dueType == null || t.dueType.equals("none")) return Urgency.NONE;

        return switch (t.dueType) {
            case "exact_date" -> byDate(t.dueDate, startOfToday, soonThreshold);

            case "range" -> {
                if (t.dueTo != null && t.dueTo < startOfToday) yield Urgency.OVERDUE;
                if (t.dueFrom != null && t.dueFrom <= soonThreshold) yield Urgency.SOON;
                yield Urgency.NORMAL;
            }

            case "by_km" -> {
                Urgency byKm = byKm(t.dueKm, tab);
                Urgency byDate = t.dueDate != null
                    ? byDate(t.dueDate, now, soonThreshold)
                    : Urgency.NONE;
                yield worse(byKm, byDate);
            }

            case "after_last" -> Urgency.NORMAL;

            default -> Urgency.NONE;
        };
    }

    private static Urgency byDate(Long dueDate, long now, long soonThreshold) {
        if (dueDate == null) return Urgency.NONE;
        if (dueDate < now) return Urgency.OVERDUE;
        if (dueDate <= soonThreshold) return Urgency.SOON;
        return Urgency.NORMAL;
    }

    private static Urgency byKm(Integer dueKm, Tab tab) {
        if (dueKm == null || tab == null || tab.currentKm == null) return Urgency.NONE;
        int remaining = dueKm - tab.currentKm;
        if (remaining <= 0) return Urgency.OVERDUE;
        if (remaining <= SOON_KM) return Urgency.SOON;
        return Urgency.NORMAL;
    }

    private static Urgency worse(Urgency a, Urgency b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(Urgency u) {
        return switch (u) {
            case OVERDUE -> 3;
            case SOON -> 2;
            case NORMAL -> 1;
            default -> 0;
        };
    }

    public enum Urgency {OVERDUE, SOON, NORMAL, NONE}
}
