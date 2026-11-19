package com.example.taskmgr.ui.tab;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.HistoryDao;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.data.dao.TaskDao;
import com.example.taskmgr.model.HistoryEntry;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.service.TaskService;
import com.example.taskmgr.service.TaskUrgencyComparator;
import com.example.taskmgr.ui.common.CompleteTaskDialog;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.button.MaterialButton;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TasksSectionController {

    private final Activity activity;
    private final long tabId;
    private final boolean isAuto;
    private final TaskDao taskDao;
    private final TabDao tabDao;
    private final HistoryDao historyDao;
    private final Runnable onMenuNeedsUpdate;

    private final RecyclerView tasksRecycler;
    private final TextView tasksEmpty;
    private final MaterialButton btnSortCreated;
    private final MaterialButton btnSortDeadline;
    private final TaskFullAdapter taskAdapter;
    private final HistoryAdapter historyAdapter;

    private boolean showingHistory = false;
    private boolean sortByDeadline = false;
    private boolean sortAscending = false;
    private int loadGeneration = 0;
    private long pendingTaskId;

    TasksSectionController(Activity activity, View root, long tabId, boolean isAuto,
                           TaskDao taskDao, TabDao tabDao, HistoryDao historyDao,
                           long initialTaskId, Runnable onMenuNeedsUpdate) {
        this.activity = activity;
        this.tabId = tabId;
        this.isAuto = isAuto;
        this.taskDao = taskDao;
        this.tabDao = tabDao;
        this.historyDao = historyDao;
        this.onMenuNeedsUpdate = onMenuNeedsUpdate;
        this.pendingTaskId = initialTaskId;

        tasksRecycler = root.findViewById(R.id.tasks_recycler);
        tasksEmpty = root.findViewById(R.id.tasks_empty);
        btnSortCreated = root.findViewById(R.id.btn_sort_created);
        btnSortDeadline = root.findViewById(R.id.btn_sort_deadline);

        tasksRecycler.setLayoutManager(new LinearLayoutManager(activity));
        taskAdapter = new TaskFullAdapter(this::showTaskActionsDialog, this::showTaskActionsDialog);
        historyAdapter = new HistoryAdapter(this::showHistoryActionsDialog);
        tasksRecycler.setAdapter(taskAdapter);

        btnSortCreated.setOnClickListener(v -> toggleSort(false));
        btnSortDeadline.setOnClickListener(v -> toggleSort(true));

        updateButtons();
        load();
    }

    boolean isShowingHistory() {
        return showingHistory;
    }

    void setHistoryMode(boolean history) {
        if (showingHistory == history) return;
        showingHistory = history;
        updateButtons();
        load();
    }

    void showAddTaskDialog() {
        TaskFormDialog.show(activity, null, tabId, isAuto, taskDao, this::load);
    }

    void load() {
        final int myGeneration = ++loadGeneration;
        final boolean history = showingHistory;
        final boolean sortDeadline = sortByDeadline;
        final boolean sortAsc = sortAscending;
        final long taskToHighlight = pendingTaskId;

        DbExecutor.run(() -> {
            if (history) {
                List<Task> allTasks = taskDao.getAllByTab(tabId);
                List<HistoryEntry> entries = historyDao.getByTab(tabId);
                return new TasksResult(entries, allTasks, null);
            }

            List<Task> activeTasks = taskDao.getByTab(tabId);
            Tab currentTab = tabDao.getById(tabId);
            if (sortDeadline && currentTab != null) {
                Map<Long, Tab> tabsById = new HashMap<>();
                tabsById.put(currentTab.id, currentTab);
                activeTasks.sort(new TaskUrgencyComparator(tabsById));
            }
            if (sortAsc) Collections.reverse(activeTasks);
            return new TasksResult(null, activeTasks, currentTab);
        }, result -> {
            if (myGeneration != loadGeneration || result == null) return;

            if (history) {
                List<HistoryEntry> entries = result.entries();
                List<Task> allTasks = result.tasks();
                tasksRecycler.setAdapter(historyAdapter);
                historyAdapter.setItems(entries, allTasks);
                boolean empty = entries.isEmpty();
                tasksEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                tasksRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            } else {
                List<Task> tasks = result.tasks();
                Tab currentTab = result.tab();
                tasksRecycler.setAdapter(taskAdapter);
                taskAdapter.setCurrentTab(currentTab);
                taskAdapter.setItems(tasks);
                boolean empty = tasks.isEmpty();
                tasksEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                tasksRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
                highlightPendingTask(taskToHighlight);
            }
        });
    }

    private void highlightPendingTask(long taskToHighlight) {
        if (taskToHighlight <= 0) return;
        pendingTaskId = -1;
        final int position = taskAdapter.positionOfTask(taskToHighlight);
        if (position < 0) return;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            tasksRecycler.scrollToPosition(position);
            taskAdapter.setHighlightedTaskId(taskToHighlight);
            new Handler(Looper.getMainLooper()).postDelayed(taskAdapter::clearHighlight, 1200);
        }, 150);
    }

    private void toggleSort(boolean byDeadline) {
        if (showingHistory) return;
        if (sortByDeadline == byDeadline) {
            sortAscending = !sortAscending;
        } else {
            sortByDeadline = byDeadline;
            sortAscending = false;
        }
        updateButtons();
        load();
    }

    private void updateButtons() {
        boolean sortEnabled = !showingHistory;
        boolean createdActive = sortEnabled && !sortByDeadline;
        boolean deadlineActive = sortEnabled && sortByDeadline;

        SegmentedButtons.apply(activity, btnSortCreated, createdActive);
        SegmentedButtons.apply(activity, btnSortDeadline, deadlineActive);

        btnSortCreated.setText("Создание");
        btnSortDeadline.setText("Дедлайн");

        if (createdActive) SegmentedButtons.setSortIcon(btnSortCreated, sortAscending);
        else btnSortCreated.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);

        if (deadlineActive) SegmentedButtons.setSortIcon(btnSortDeadline, sortAscending);
        else btnSortDeadline.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);

        btnSortCreated.setEnabled(sortEnabled);
        btnSortDeadline.setEnabled(sortEnabled);

        if (onMenuNeedsUpdate != null) onMenuNeedsUpdate.run();
    }

    private void showTaskActionsDialog(Task task) {
        String[] actions = {"Выполнено", "Редактировать", "Удалить"};
        new AlertDialog.Builder(activity)
            .setTitle(task.title)
            .setItems(actions, (d, which) -> {
                if (which == 0) {
                    CompleteTaskDialog.show(activity, task, isAuto, t -> load());
                } else if (which == 1) {
                    TaskFormDialog.show(activity, task, tabId, isAuto, taskDao, this::load);
                } else {
                    DbExecutor.runVoid(() -> taskDao.delete(task.id), this::load);
                }
            })
            .show();
    }

    private void showHistoryActionsDialog(HistoryEntry entry, Task task) {
        if (entry == null) return;
        String title = (task != null) ? task.title : ("Задача #" + entry.taskId);
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(new String[]{"Вернуть в задачи"}, (d, which) -> {
                if (which != 0) return;
                final long taskId = entry.taskId;
                final long historyId = entry.id;
                DbExecutor.runVoid(() -> {
                    TaskService service = new TaskService(activity);
                    service.revertCompletedTask(taskId, historyId);
                }, this::load);
            })
            .show();
    }

    private record TasksResult(List<HistoryEntry> entries, List<Task> tasks, Tab tab) {
    }
}
