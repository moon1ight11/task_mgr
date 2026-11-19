package com.example.taskmgr.ui.tab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.TaskDao;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.button.MaterialButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class TaskFormDialog {

    private final Activity activity;
    private final Task existing;
    private final long tabId;
    private final boolean isAuto;
    private final TaskDao taskDao;
    private final Runnable onSaved;
    private final int[] priorityIndex = {1};
    private final int[] dueIndex = {0};
    private EditText inputTitle;
    private EditText inputDescription;
    private EditText inputDueDate;
    private EditText inputDueFrom;
    private EditText inputDueTo;
    private EditText inputDueKm;
    private EditText inputRepeatKm;
    private EditText inputRepeatMonths;
    private Spinner spinnerRepeatRule;
    private String[] dueTypes;
    private String[] repeatRules;
    private List<MaterialButton> priorityButtons;
    private List<MaterialButton> dueButtons;
    private Runnable refreshDueFields;

    private View dialogView;

    private TaskFormDialog(Activity activity, Task existing, long tabId, boolean isAuto,
                           TaskDao taskDao, Runnable onSaved) {
        this.activity = activity;
        this.existing = existing;
        this.tabId = tabId;
        this.isAuto = isAuto;
        this.taskDao = taskDao;
        this.onSaved = onSaved;
    }

    static void show(Activity activity, Task task, long tabId, boolean isAuto,
                     TaskDao taskDao, Runnable onSaved) {
        new TaskFormDialog(activity, task, tabId, isAuto, taskDao, onSaved).show();
    }

    private static int priorityToIndex(String priority) {
        if ("important".equals(priority)) return 0;
        if ("whatever".equals(priority)) return 2;
        return 1;
    }

    private static String indexToPriority(int index) {
        return switch (index) {
            case 0 -> "important";
            case 2 -> "whatever";
            default -> "normal";
        };
    }

    @SuppressLint("InflateParams")
    private void show() {
        dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        bindViews(dialogView);
        setupDatePickers();
        setupPriorityButtons();
        setupDueTypeButtons();
        setupRepeatRule();

        if (existing != null) {
            prefillExisting();
        }

        new AlertDialog.Builder(activity)
            .setTitle(existing == null ? "Новая задача" : "Редактировать задачу")
            .setView(dialogView)
            .setPositiveButton(existing == null ? "Добавить" : "Сохранить",
                (d, w) -> save())
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void bindViews(View view) {
        inputTitle = view.findViewById(R.id.input_title);
        inputDescription = view.findViewById(R.id.input_description);
        spinnerRepeatRule = view.findViewById(R.id.spinner_repeat_rule);
        inputDueDate = view.findViewById(R.id.input_due_date);
        inputDueFrom = view.findViewById(R.id.input_due_from);
        inputDueTo = view.findViewById(R.id.input_due_to);
        inputDueKm = view.findViewById(R.id.input_due_km);
        inputRepeatKm = view.findViewById(R.id.input_repeat_km);
        inputRepeatMonths = view.findViewById(R.id.input_repeat_months);
    }

    private void setupDatePickers() {
        pickDate(inputDueDate);
        pickDate(inputDueFrom);
        pickDate(inputDueTo);
    }

    private void setupPriorityButtons() {
        priorityIndex[0] = existing != null ? priorityToIndex(existing.priority) : 1;

        MaterialButton important = findInDialogView(R.id.btn_priority_important);
        MaterialButton normal = findInDialogView(R.id.btn_priority_normal);
        MaterialButton whatever = findInDialogView(R.id.btn_priority_whatever);

        priorityButtons = new ArrayList<>();
        priorityButtons.add(important);
        priorityButtons.add(normal);
        priorityButtons.add(whatever);
        SegmentedButtons.applyGroup(activity, priorityButtons, priorityIndex[0]);

        important.setOnClickListener(v -> {
            priorityIndex[0] = 0;
            SegmentedButtons.applyGroup(activity, priorityButtons, 0);
        });
        normal.setOnClickListener(v -> {
            priorityIndex[0] = 1;
            SegmentedButtons.applyGroup(activity, priorityButtons, 1);
        });
        whatever.setOnClickListener(v -> {
            priorityIndex[0] = 2;
            SegmentedButtons.applyGroup(activity, priorityButtons, 2);
        });
    }

    private void setupDueTypeButtons() {
        MaterialButton dueNone = findInDialogView(R.id.btn_due_none);
        MaterialButton dueExact = findInDialogView(R.id.btn_due_exact);
        MaterialButton dueRange = findInDialogView(R.id.btn_due_range);
        MaterialButton dueKm = findInDialogView(R.id.btn_due_km);

        dueTypes = isAuto
            ? new String[]{"none", "exact_date", "range", "by_km"}
            : new String[]{"none", "exact_date", "range"};

        dueButtons = new ArrayList<>();
        dueButtons.add(dueNone);
        dueButtons.add(dueExact);
        dueButtons.add(dueRange);
        if (isAuto) dueButtons.add(dueKm);
        else dueKm.setVisibility(View.GONE);

        int initial = 0;
        if (existing != null && existing.dueType != null) {
            for (int i = 0; i < dueTypes.length; i++) {
                if (dueTypes[i].equals(existing.dueType)) {
                    initial = i;
                    break;
                }
            }
        }
        dueIndex[0] = initial;
        SegmentedButtons.applyGroup(activity, dueButtons, dueIndex[0]);

        refreshDueFields = () -> {
            String type = dueTypes[dueIndex[0]];
            inputDueDate.setVisibility("exact_date".equals(type) ? View.VISIBLE : View.GONE);
            inputDueFrom.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
            inputDueTo.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
            inputDueKm.setVisibility("by_km".equals(type) ? View.VISIBLE : View.GONE);
        };

        dueNone.setOnClickListener(v -> selectDueType(0));
        dueExact.setOnClickListener(v -> selectDueType(1));
        dueRange.setOnClickListener(v -> selectDueType(2));
        if (isAuto) dueKm.setOnClickListener(v -> selectDueType(3));
        refreshDueFields.run();
    }

    private void selectDueType(int index) {
        dueIndex[0] = index;
        SegmentedButtons.applyGroup(activity, dueButtons, index);
        refreshDueFields.run();
    }

    private void setupRepeatRule() {
        if (isAuto) {
            repeatRules = new String[]{"none", "by_km", "by_months", "by_km_or_months"};
            setSpinner(spinnerRepeatRule,
                new String[]{"без повтора", "по пробегу", "по месяцам", "по пробегу или месяцам"});
            spinnerRepeatRule.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                    String rule = repeatRules[pos];
                    inputRepeatKm.setVisibility(
                        ("by_km".equals(rule) || "by_km_or_months".equals(rule))
                            ? View.VISIBLE : View.GONE);
                    inputRepeatMonths.setVisibility(
                        ("by_months".equals(rule) || "by_km_or_months".equals(rule))
                            ? View.VISIBLE : View.GONE);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        } else {
            repeatRules = new String[]{"none"};
            spinnerRepeatRule.setVisibility(View.GONE);
            inputRepeatKm.setVisibility(View.GONE);
            inputRepeatMonths.setVisibility(View.GONE);
            hideRepeatLabel();
        }
    }

    private void hideRepeatLabel() {
        View repeatParent = (View) spinnerRepeatRule.getParent();
        if (!(repeatParent instanceof android.view.ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView
                && "Повтор".contentEquals(((TextView) child).getText())) {
                child.setVisibility(View.GONE);
            }
        }
    }

    private void prefillExisting() {
        inputTitle.setText(existing.title);
        if (existing.description != null) inputDescription.setText(existing.description);

        if ("exact_date".equals(existing.dueType) && existing.dueDate != null) {
            inputDueDate.setText(formatDateForInput(existing.dueDate));
        } else if ("range".equals(existing.dueType)) {
            if (existing.dueFrom != null)
                inputDueFrom.setText(formatDateForInput(existing.dueFrom));
            if (existing.dueTo != null) inputDueTo.setText(formatDateForInput(existing.dueTo));
        } else if ("by_km".equals(existing.dueType) && existing.dueKm != null) {
            inputDueKm.setText(String.valueOf(existing.dueKm));
        }

        if (isAuto && existing.repeatRule != null) {
            int repIdx = 0;
            for (int i = 0; i < repeatRules.length; i++) {
                if (repeatRules[i].equals(existing.repeatRule)) {
                    repIdx = i;
                    break;
                }
            }
            spinnerRepeatRule.setSelection(repIdx);
            if ((repIdx == 1 || repIdx == 3) && existing.repeatIntervalKm != null) {
                inputRepeatKm.setText(String.valueOf(existing.repeatIntervalKm));
            }
            if ((repIdx == 2 || repIdx == 3) && existing.repeatIntervalMonths != null) {
                inputRepeatMonths.setText(String.valueOf(existing.repeatIntervalMonths));
            }
        }
    }

    private void save() {
        String title = inputTitle.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(activity, "Введите название", Toast.LENGTH_SHORT).show();
            return;
        }

        Task task = existing != null
            ? existing
            : new Task(tabId, title, indexToPriority(priorityIndex[0]));
        task.title = title;
        task.priority = indexToPriority(priorityIndex[0]);

        String desc = inputDescription.getText().toString().trim();
        task.description = desc.isEmpty() ? null : desc;

        task.dueType = dueTypes[dueIndex[0]];
        task.dueDate = null;
        task.dueFrom = null;
        task.dueTo = null;
        task.dueKm = null;
        if ("exact_date".equals(task.dueType)) {
            task.dueDate = parseDate(inputDueDate.getText().toString());
        } else if ("range".equals(task.dueType)) {
            task.dueFrom = parseDate(inputDueFrom.getText().toString());
            task.dueTo = parseDate(inputDueTo.getText().toString());
        } else if ("by_km".equals(task.dueType)) {
            task.dueKm = parseIntOrNull(inputDueKm.getText().toString());
        }

        if (isAuto) {
            int repPos = spinnerRepeatRule.getSelectedItemPosition();
            task.repeatRule = repeatRules[repPos];
            task.repeatIntervalKm = null;
            task.repeatIntervalMonths = null;
            if ("by_km".equals(task.repeatRule) || "by_km_or_months".equals(task.repeatRule)) {
                task.repeatIntervalKm = parseIntOrNull(inputRepeatKm.getText().toString());
            }
            if ("by_months".equals(task.repeatRule) || "by_km_or_months".equals(task.repeatRule)) {
                task.repeatIntervalMonths = parseIntOrNull(inputRepeatMonths.getText().toString());
            }
        } else {
            task.repeatRule = "none";
            task.repeatIntervalKm = null;
            task.repeatIntervalMonths = null;
        }

        final boolean insert = existing == null;
        DbExecutor.runVoid(
            () -> {
                if (insert) taskDao.insert(task);
                else taskDao.update(task);
            },
            onSaved);
    }

    private MaterialButton findInDialogView(int id) {
        if (dialogView == null) throw new IllegalStateException("dialog view not bound");
        return dialogView.findViewById(id);
    }

    private void setSpinner(Spinner spinner, String[] labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            activity, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
    }

    private void pickDate(EditText target) {
        target.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            Long existingValue = parseDate(target.getText().toString());
            if (existingValue != null) cal.setTimeInMillis(existingValue);
            new DatePickerDialog(activity, (dp, year, month, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, day, 0, 0, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                target.setText(formatDateForInput(chosen.getTimeInMillis()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private String formatDateForInput(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    private Long parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            sdf.setLenient(false);
            Date date = sdf.parse(value.trim());
            return date != null ? date.getTime() : null;
        } catch (ParseException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
