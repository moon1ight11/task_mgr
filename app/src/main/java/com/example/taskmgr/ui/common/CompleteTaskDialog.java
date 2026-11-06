package com.example.taskmgr.ui.common;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.service.TaskService;
import com.example.taskmgr.util.DbExecutor;

/**
 * Универсальный диалог выполнения задачи.
 * Используется и в TabActivity (вкладка «Задачи»), и в MainActivity (главная).
 */
public final class CompleteTaskDialog {

    private CompleteTaskDialog() {}

    public interface OnCompleted {
        void onCompleted(Task task);
    }

    /**
     * Показывает диалог выполнения задачи.
     *
     * @param activity текущая Activity
     * @param task     задача
     * @param isAuto   признак авто-вкладки (для подсказки пробега)
     * @param onDone   колбэк после успешного выполнения (может быть null)
     */
    public static void show(Activity activity, Task task, boolean isAuto, OnCompleted onDone) {
        if (activity == null || task == null || activity.isFinishing()) return;

        boolean needsKm = isAuto
                || "by_km".equals(task.dueType)
                || "by_km".equals(task.repeatRule)
                || "by_km_or_months".equals(task.repeatRule);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_complete_task, null);
        EditText inputKm = view.findViewById(R.id.input_km);
        EditText inputNote = view.findViewById(R.id.input_note);
        inputKm.setVisibility(needsKm ? View.VISIBLE : View.GONE);

        if (needsKm && isAuto) {
            final long tabId = task.tabId;
            DbExecutor.run(() -> new TabDao(activity).getById(tabId), tab -> {
                if (tab != null && tab.currentKm != null && inputKm.getText().toString().isEmpty()) {
                    inputKm.setHint("Пробег, км (сейчас: " + tab.currentKm + ")");
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Выполнить: " + task.title)
                .setView(view)
                .setPositiveButton("OK", null)
                .setNegativeButton("Отмена", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Integer km = null;
                if (needsKm) {
                    String s = inputKm.getText().toString().trim();
                    if (!s.isEmpty()) {
                        try {
                            km = Integer.parseInt(s);
                            if (km < 0) {
                                Toast.makeText(activity, "Пробег не может быть отрицательным",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(activity, "Некорректный пробег", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
                String note = inputNote.getText().toString().trim();
                final Integer kmFinal = km;
                final String noteFinal = note.isEmpty() ? null : note;

                dialog.dismiss();
                DbExecutor.runVoid(() -> {
                    TaskService service = new TaskService(activity);
                    service.completeTask(task, kmFinal, noteFinal);
                }, () -> {
                    Toast.makeText(activity, "Выполнено: " + task.title, Toast.LENGTH_SHORT).show();
                    if (onDone != null) onDone.onCompleted(task);
                });
            });
        });

        dialog.show();
    }
}