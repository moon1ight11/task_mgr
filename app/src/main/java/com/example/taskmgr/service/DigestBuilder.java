package com.example.taskmgr.service;

import android.content.Context;

import com.example.taskmgr.data.dao.AppSettingsDao;
import com.example.taskmgr.data.dao.SectionDao;
import com.example.taskmgr.data.dao.SubscriptionDao;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.data.dao.TaskDao;
import com.example.taskmgr.model.Section;
import com.example.taskmgr.model.Subscription;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.util.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DigestBuilder {

    public static final int MAX_LINES = 10;
    public static final int MAX_SUB_LINES = 5;

    private DigestBuilder() {
    }

    public static Digest build(Context context, boolean forTomorrow) {
        TaskDao taskDao = new TaskDao(context);
        TabDao tabDao = new TabDao(context);
        SectionDao sectionDao = new SectionDao(context);
        SubscriptionDao subscriptionDao = new SubscriptionDao(context);
        AppSettingsDao settingsDao = new AppSettingsDao(context);

        long dayStart = DateUtils.startOfDay(System.currentTimeMillis());
        if (forTomorrow) {
            dayStart += 24L * 60 * 60 * 1000;
        }
        long dayEnd = dayStart + 24L * 60 * 60 * 1000;

        // Задачи на день
        List<Task> tasks = taskDao.getTasksForDay(dayStart, dayEnd);

        // Подписки — только в утренней сводке (forTomorrow == false),
        // в окне [сегодня; сегодня + N дней], где N — из настроек
        List<Subscription> subs = null;
        int subsDays = 0;
        if (!forTomorrow && isSubsEnabled(settingsDao)) {
            subsDays = readSubsDays(settingsDao);
            long subsFrom = DateUtils.startOfDay(System.currentTimeMillis());
            long subsTo = subsFrom + (long) subsDays * 24L * 60 * 60 * 1000;
            subs = subscriptionDao.getUpcomingWithin(subsFrom, subsTo);
        }

        boolean hasTasks = !tasks.isEmpty();
        boolean hasSubs = subs != null && !subs.isEmpty();

        if (!hasTasks && !hasSubs) return null;

        Map<Long, Tab> tabsById = new HashMap<>();
        for (Tab t : tabDao.getAll()) tabsById.put(t.id, t);
        Map<Long, String> sectionsById = new HashMap<>();
        for (Section s : sectionDao.getAll()) sectionsById.put(s.id, s.name);

        int important = countImportant(tasks);
        String title = buildTitle(tasks.size(), important, forTomorrow, hasSubs, subs, subsDays);
        String greeting = buildGreeting(tasks.size(), important, forTomorrow, hasSubs, subs, subsDays);

        StringBuilder sb = new StringBuilder();
        sb.append(greeting);

        // Задачи
        if (hasTasks) {
            int shown = 0;
            for (Task t : tasks) {
                if (shown >= MAX_LINES) break;
                sb.append('\n');
                sb.append(buildTaskLine(t, tabsById, sectionsById));
                shown++;
            }
            if (tasks.size() > MAX_LINES) {
                sb.append('\n').append("…и ещё ").append(tasks.size() - MAX_LINES);
            }
        }

        // Подписки
        if (hasSubs) {
            appendSubscriptionsBlock(context, sb, subs, subsDays);
        }

        return new Digest(title, greeting, sb.toString(), tasks.size());
    }

    // ---------- Подписки ----------

    private static boolean isSubsEnabled(AppSettingsDao dao) {
        String v = dao.get(AppSettingsDao.KEY_NOTIF_SUBS_ENABLED);
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    private static int readSubsDays(AppSettingsDao dao) {
        String v = dao.get(AppSettingsDao.KEY_NOTIF_SUBS_DAYS);
        if (v == null) return AppSettingsDao.DEFAULT_SUBS_DAYS;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return AppSettingsDao.DEFAULT_SUBS_DAYS;
        }
    }

    private static void appendSubscriptionsBlock(Context context, StringBuilder sb,
                                                 List<Subscription> subs, int days) {
        if (subs == null || subs.isEmpty()) return;

        String header;
        if (days == 1) {
            header = "Списание завтра:";
        } else {
            header = "Списания в ближайшие " + days + " дн.:";
        }
        sb.append('\n').append('\n').append(header);

        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM", new Locale("ru"));
        int shown = 0;
        for (Subscription s : subs) {
            if (shown >= MAX_SUB_LINES) break;
            sb.append('\n')
                .append("• ")
                .append(s.name)
                .append(" — ")
                .append(String.format(Locale.getDefault(), "%.2f", s.amount))
                .append(" ₽ · ")
                .append(fmt.format(new Date(s.nextChargeAt)));
            shown++;
        }
        if (subs.size() > MAX_SUB_LINES) {
            sb.append('\n').append("…и ещё ").append(subs.size() - MAX_SUB_LINES);
        }
    }

    // ---------- Задачи ----------

    private static int countImportant(List<Task> tasks) {
        int n = 0;
        for (Task t : tasks) if ("important".equals(t.priority)) n++;
        return n;
    }

    private static String buildTitle(int count, int important, boolean forTomorrow,
                                     boolean hasSubs, List<Subscription> subs, int subsDays) {
        String when = forTomorrow ? "Завтра" : "Сегодня";
        StringBuilder sb = new StringBuilder(when).append(": ");
        if (count > 0) {
            sb.append(count).append(' ').append(tasksWord(count));
            if (important > 0) {
                sb.append(" • ").append(important).append(' ').append(importantWord(important));
            }
        } else {
            sb.append("задач нет");
        }
        if (hasSubs && subs != null && !subs.isEmpty()) {
            sb.append(" • списаний: ").append(subs.size());
        }
        return sb.toString();
    }

    private static String buildGreeting(int count, int important, boolean forTomorrow,
                                        boolean hasSubs, List<Subscription> subs, int subsDays) {
        StringBuilder sb = new StringBuilder();
        if (forTomorrow) {
            sb.append("Привет! Не забудь, что завтра у тебя ")
                .append(count).append(' ').append(tasksWord(count))
                .append(". Заполни ежедневник :)");
            return sb.toString();
        }

        if (count == 0 && hasSubs) {
            sb.append("Доброго утра! Задач на сегодня нет, но есть списания:");
            return sb.toString();
        }

        sb.append("Доброго утра! ");
        if (count > 0) {
            sb.append("Не забудь, сегодня у тебя ")
                .append(count).append(' ').append(tasksWord(count));
            if (important > 0) {
                sb.append(", из них ").append(important).append(' ').append(importantWord(important));
            }
            sb.append('.');
        } else {
            sb.append("Задач на сегодня нет.");
        }
        if (hasSubs) {
            sb.append(" Также не забудь про списания.");
        }
        return sb.toString();
    }

    private static String buildTaskLine(Task t, Map<Long, Tab> tabsById,
                                        Map<Long, String> sectionsById) {
        StringBuilder line = new StringBuilder("• ").append(t.title);
        Tab tab = tabsById.get(t.tabId);
        if (tab == null) return line.toString();

        String section = sectionsById.get(tab.sectionId);
        String tabName = tab.name;
        boolean hasSection = section != null && !section.isEmpty();
        boolean hasTab = tabName != null && !tabName.isEmpty();

        if (hasSection) line.append(" — ").append(section);
        if (hasSection && hasTab) line.append(" • ").append(tabName);
        else if (!hasSection && hasTab) line.append(" — ").append(tabName);

        return line.toString();
    }

    private static String tasksWord(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "задача";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "задачи";
        return "задач";
    }

    private static String importantWord(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "важная";
        return "важных";
    }

    public static class Digest {
        public final String title;
        public final String greeting;
        public final String bigText;
        public final int taskCount;

        Digest(String title, String greeting, String bigText, int taskCount) {
            this.title = title;
            this.greeting = greeting;
            this.bigText = bigText;
            this.taskCount = taskCount;
        }
    }
}
