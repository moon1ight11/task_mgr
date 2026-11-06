package com.example.taskmgr.service;

import android.content.Context;

import com.example.taskmgr.data.dao.HistoryDao;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.data.dao.TaskDao;
import com.example.taskmgr.model.HistoryEntry;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;

public class TaskService {

    private final TaskDao taskDao;
    private final HistoryDao historyDao;
    private final TabDao tabDao;

    public TaskService(Context context) {
        this.taskDao = new TaskDao(context);
        this.historyDao = new HistoryDao(context);
        this.tabDao = new TabDao(context);
    }

    /**
     * Выполняет задачу: пишет историю, помечает задачу done,
     * обновляет пробег вкладки (если передан),
     * и если есть repeat_rule — создаёт следующую задачу.
     *
     * @param task           задача
     * @param kmAtCompletion пробег на момент выполнения (может быть null)
     * @param note           заметка (может быть null)
     */
    public void completeTask(Task task, Integer kmAtCompletion, String note) {
        long now = System.currentTimeMillis();

        // 1. История
        HistoryEntry h = new HistoryEntry();
        h.taskId = task.id;
        h.completedAt = now;
        h.kmAtCompletion = kmAtCompletion;
        h.note = note;
        historyDao.insert(h);

        // 2. Помечаем задачу выполненной
        taskDao.updateStatus(task.id, "done");

        // 3. Обновляем пробег вкладки (машины) — ВСЕГДА, если он передан.
        //    Одометр не уменьшается: пишем только если новое значение больше
        //    текущего (или если текущего нет).
        if (kmAtCompletion != null) {
            updateTabKmIfNeeded(task.tabId, kmAtCompletion);
        }

        // 4. Автопорождение следующей
        if (task.repeatRule != null && !task.repeatRule.equals("none")) {
            createNextTask(task, now, kmAtCompletion);
        }
    }

    private void updateTabKmIfNeeded(long tabId, int km) {
        Tab tab = tabDao.getById(tabId);
        if (tab == null) return;
        if (tab.currentKm == null || km > tab.currentKm) {
            tab.currentKm = km;
            tabDao.update(tab);
        }
    }

    private void createNextTask(Task done, long completedAt, Integer kmAtCompletion) {
        Task next = new Task(done.tabId, done.title, done.priority);
        next.description = done.description;
        next.repeatRule = done.repeatRule;
        next.repeatIntervalKm = done.repeatIntervalKm;
        next.repeatIntervalMonths = done.repeatIntervalMonths;
        next.status = "active";

        switch (done.repeatRule) {
            case "by_km":
                // следующая — по пробегу
                if (kmAtCompletion != null && done.repeatIntervalKm != null) {
                    next.dueType = "by_km";
                    next.dueKm = kmAtCompletion + done.repeatIntervalKm;
                } else {
                    // не хватает данных — создаём без срока
                    next.dueType = "none";
                }
                break;

            case "by_months":
                // следующая — по дате
                if (done.repeatIntervalMonths != null) {
                    next.dueType = "exact_date";
                    next.dueDate = addMonths(completedAt, done.repeatIntervalMonths);
                } else {
                    next.dueType = "none";
                }
                break;

            case "by_km_or_months": {
                Long nextDate = done.repeatIntervalMonths != null
                        ? addMonths(completedAt, done.repeatIntervalMonths) : null;
                Integer nextKm = (kmAtCompletion != null && done.repeatIntervalKm != null)
                        ? kmAtCompletion + done.repeatIntervalKm : null;

                if (nextKm != null) {
                    next.dueType = "by_km";
                    next.dueKm = nextKm;
                    next.dueDate = nextDate; // сохраняем и дату, пригодится для срочности
                } else if (nextDate != null) {
                    next.dueType = "exact_date";
                    next.dueDate = nextDate;
                } else {
                    next.dueType = "none";
                }
                break;
            }

            default:
                next.dueType = "none";
        }

        taskDao.insert(next);
    }

    private long addMonths(long millis, int months) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.add(java.util.Calendar.MONTH, months);
        return cal.getTimeInMillis();
    }
}