package com.example.taskmgr.service;

import android.content.Context;

import com.example.taskmgr.data.dao.SectionDao;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.data.dao.TaskDao;
import com.example.taskmgr.model.Section;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.util.DateUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DigestBuilder {

    public static final int MAX_LINES = 10;

    private DigestBuilder() {
    }

    public static Digest build(Context context, boolean forTomorrow) {
        TaskDao taskDao = new TaskDao(context);
        TabDao tabDao = new TabDao(context);
        SectionDao sectionDao = new SectionDao(context);

        long dayStart = DateUtils.startOfDay(System.currentTimeMillis());
        if (forTomorrow) {
            dayStart += 24L * 60 * 60 * 1000;
        }
        long dayEnd = dayStart + 24L * 60 * 60 * 1000;

        List<Task> tasks = taskDao.getTasksForDay(dayStart, dayEnd);
        if (tasks.isEmpty()) return null;

        Map<Long, Tab> tabsById = new HashMap<>();
        for (Tab t : tabDao.getAll()) tabsById.put(t.id, t);
        Map<Long, String> sectionsById = new HashMap<>();
        for (Section s : sectionDao.getAll()) sectionsById.put(s.id, s.name);

        int important = countImportant(tasks);
        String title = buildTitle(tasks.size(), important, forTomorrow);
        String greeting = buildGreeting(tasks.size(), important, forTomorrow);

        StringBuilder sb = new StringBuilder();
        sb.append(greeting).append('\n');
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

        return new Digest(title, greeting, sb.toString(), tasks.size());
    }

    private static int countImportant(List<Task> tasks) {
        int n = 0;
        for (Task t : tasks) if ("important".equals(t.priority)) n++;
        return n;
    }

    private static String buildTitle(int count, int important, boolean forTomorrow) {
        String when = forTomorrow ? "Завтра" : "Сегодня";
        return when + ": " + count + " " + tasksWord(count)
            + (important > 0 ? " • " + important + " " + importantWord(important) : "");
    }

    private static String buildGreeting(int count, int important, boolean forTomorrow) {
        if (forTomorrow) {
            return "Привет! Не забудь, что завтра у тебя "
                + count + " " + tasksWord(count)
                + ". Заполни ежедневник :)";
        }
        return "Доброго утра! Не забудь, сегодня у тебя "
            + count + " " + tasksWord(count)
            + (important > 0
            ? ", из них " + important + " " + importantWord(important)
            : "");
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
