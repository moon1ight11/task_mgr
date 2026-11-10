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

public final class CompleteTaskDialog {

    private CompleteTaskDialog() {
    }

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

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                handlePositiveClick(activity, task, needsKm, inputKm, inputNote, dialog, onDone)));

        dialog.show();
    }

    private static void handlePositiveClick(Activity activity, Task task, boolean needsKm,
                                            EditText inputKm, EditText inputNote,
                                            AlertDialog dialog, OnCompleted onDone) {
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
        DbExecutor.runVoid(
            () -> new TaskService(activity).completeTask(task, kmFinal, noteFinal),
            () -> showCompleted(activity, task, onDone)
        );
    }

    private static void showCompleted(Activity activity, Task task, OnCompleted onDone) {
        Toast.makeText(activity, "Выполнено: " + task.title, Toast.LENGTH_SHORT).show();
        if (onDone != null) onDone.onCompleted(task);
    }

    public interface OnCompleted {
        void onCompleted(Task task);
    }
}
