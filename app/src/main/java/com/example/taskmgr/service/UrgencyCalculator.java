package com.example.taskmgr.service;

import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;

public class UrgencyCalculator {

    public enum Urgency { OVERDUE, SOON, NORMAL, NONE }

    private static final int SOON_DAYS = 3;
    private static final int SOON_KM = 500;

    public static Urgency calculate(Task t, Tab tab) {
        long now = System.currentTimeMillis();
        long soonThreshold = now + (long) SOON_DAYS * 24 * 60 * 60 * 1000;

        if (t.dueType == null || t.dueType.equals("none")) return Urgency.NONE;

        switch (t.dueType) {
            case "exact_date":
                return byDate(t.dueDate, now, soonThreshold);

            case "range":
                if (t.dueTo != null && t.dueTo < now) return Urgency.OVERDUE;
                if (t.dueFrom != null && t.dueFrom <= soonThreshold) return Urgency.SOON;
                return Urgency.NORMAL;

            case "by_km": {
                Urgency byKm = byKm(t.dueKm, tab);
                Urgency byDate = t.dueDate != null
                        ? byDate(t.dueDate, now, soonThreshold)
                        : Urgency.NONE;
                return worse(byKm, byDate);
            }

            case "after_last":
                return Urgency.NORMAL;

            default:
                return Urgency.NONE;
        }
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

    /** Возвращает более срочную из двух оценок. */
    private static Urgency worse(Urgency a, Urgency b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(Urgency u) {
        switch (u) {
            case OVERDUE: return 3;
            case SOON:    return 2;
            case NORMAL:  return 1;
            default:      return 0;
        }
    }
}