package com.example.taskmgr.ui.finance;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.FinanceIncomeDao;
import com.example.taskmgr.data.dao.FinanceTemplateDao;
import com.example.taskmgr.model.FinanceIncome;
import com.example.taskmgr.model.FinanceTemplate;
import com.example.taskmgr.util.DbExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FinanceSettingsController {

    private final Activity activity;
    private final View root;
    private final FinanceIncomeDao incomeDao;
    private final FinanceTemplateDao templateDao;

    public FinanceSettingsController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        incomeDao = new FinanceIncomeDao(activity);
        templateDao = new FinanceTemplateDao(activity);

        root.findViewById(R.id.btn_add_income).setOnClickListener(v -> showAddIncomeDialog());
        root.findViewById(R.id.btn_add_template).setOnClickListener(v -> showAddTemplateDialog());
    }

    public void load() {
        DbExecutor.run(() -> {
            List<FinanceIncome> inc = incomeDao.getAll(false);
            List<FinanceTemplate> tpl = templateDao.getAll(false);
            return new LoadResult(inc, tpl);
        }, res -> {
            if (res == null) return;
            bindIncomes(res.incomes);
            bindTemplates(res.templates);
        });
    }

    private void bindIncomes(List<FinanceIncome> list) {
        LinearLayout container = root.findViewById(R.id.container_incomes);
        container.removeAllViews();
        if (list.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Поступлений нет. Добавьте хотя бы одно.");
            empty.setTextColor(ContextCompat.getColor(activity, R.color.text_tertiary));
            empty.setPadding(0, dp(6), 0, dp(6));
            container.addView(empty);
            return;
        }
        for (FinanceIncome i : list) {
            container.addView(buildIncomeRow(i));
        }
    }

    private View buildIncomeRow(FinanceIncome i) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView main = new TextView(activity);
        String name = i.name != null && !i.name.isEmpty() ? i.name : "Поступление";
        main.setText(String.format(Locale.getDefault(),
            "%s · %d-е · %.2f ₽", name, i.dayOfMonth, i.amount));
        main.setTextSize(14);
        main.setTextColor(ContextCompat.getColor(activity,
            i.isActive ? R.color.text_primary : R.color.text_tertiary));
        main.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(main);

        row.setOnClickListener(v -> showIncomeActions(i));
        return row;
    }

    private void showIncomeActions(FinanceIncome i) {
        new AlertDialog.Builder(activity)
            .setTitle(i.name != null ? i.name : "Поступление")
            .setItems(new String[]{"Редактировать", "Удалить"}, (d, w) -> {
                if (w == 0) showIncomeDialog(i);
                else DbExecutor.runVoid(() -> incomeDao.delete(i.id), this::load);
            })
            .show();
    }

    public void showAddIncomeDialog() {
        showIncomeDialog(null);
    }

    private void showIncomeDialog(FinanceIncome existing) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_finance_income, null);
        EditText inName = v.findViewById(R.id.input_income_name);
        EditText inAmount = v.findViewById(R.id.input_income_amount);
        EditText inDay = v.findViewById(R.id.input_income_day);

        if (existing != null) {
            if (existing.name != null) inName.setText(existing.name);
            inAmount.setText(String.format(Locale.US, "%.2f", existing.amount));
            inDay.setText(String.valueOf(existing.dayOfMonth));
        }

        new AlertDialog.Builder(activity)
            .setTitle(existing == null ? "Новое поступление" : "Редактировать поступление")
            .setView(v)
            .setPositiveButton("Сохранить", (d, w) -> {
                String name = inName.getText().toString().trim();
                double amt;
                try {
                    amt = Double.parseDouble(inAmount.getText().toString().trim().replace(',', '.'));
                    if (amt < 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    Toast.makeText(activity, "Некорректная сумма", Toast.LENGTH_SHORT).show();
                    return;
                }
                int day;
                try {
                    day = Integer.parseInt(inDay.getText().toString().trim());
                    if (day < 1 || day > 31) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    Toast.makeText(activity, "День 1–31", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (existing == null) {
                    FinanceIncome inc = new FinanceIncome(
                        name.isEmpty() ? null : name, amt, day);
                    DbExecutor.runVoid(() -> incomeDao.insert(inc), this::load);
                } else {
                    existing.name = name.isEmpty() ? null : name;
                    existing.amount = amt;
                    existing.dayOfMonth = day;
                    DbExecutor.runVoid(() -> incomeDao.update(existing), this::load);
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    // ---- шаблоны ----

    private void bindTemplates(List<FinanceTemplate> list) {
        LinearLayout container = root.findViewById(R.id.container_templates);
        container.removeAllViews();
        if (list.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Шаблонов нет.");
            empty.setTextColor(ContextCompat.getColor(activity, R.color.text_tertiary));
            empty.setPadding(0, dp(6), 0, dp(6));
            container.addView(empty);
            return;
        }
        for (FinanceTemplate t : list) {
            container.addView(buildTemplateRow(t));
        }
    }

    private View buildTemplateRow(FinanceTemplate t) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView cat = new TextView(activity);
        cat.setText(t.category);
        cat.setTextSize(14);
        cat.setTypeface(cat.getTypeface(), android.graphics.Typeface.BOLD);
        cat.setTextColor(ContextCompat.getColor(activity,
            t.isActive ? R.color.text_primary : R.color.text_tertiary));
        textCol.addView(cat);

        if (t.title != null && !t.title.isEmpty()) {
            TextView sub = new TextView(activity);
            sub.setText(t.title);
            sub.setTextSize(12);
            sub.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary));
            textCol.addView(sub);
        }

        row.addView(textCol);

        TextView amt = new TextView(activity);
        amt.setText(String.format(Locale.getDefault(), "%.2f ₽", t.defaultAmount));
        amt.setTextSize(14);
        amt.setTextColor(ContextCompat.getColor(activity, R.color.neon_green));
        amt.setPadding(dp(8), 0, 0, 0);
        row.addView(amt);

        row.setOnClickListener(v -> showTemplateActions(t));
        return row;
    }

    private void showTemplateActions(FinanceTemplate t) {
        new AlertDialog.Builder(activity)
            .setTitle(t.category)
            .setItems(new String[]{"Редактировать", "Удалить"}, (d, w) -> {
                if (w == 0) showTemplateDialog(t);
                else DbExecutor.runVoid(() -> templateDao.delete(t.id), this::load);
            })
            .show();
    }

    private void showAddTemplateDialog() {
        showTemplateDialog(null);
    }

    private void showTemplateDialog(FinanceTemplate existing) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_finance_template, null);
        EditText inCat = v.findViewById(R.id.input_template_category);
        EditText inTitle = v.findViewById(R.id.input_template_title);
        EditText inAmt = v.findViewById(R.id.input_template_amount);

        if (existing != null) {
            inCat.setText(existing.category);
            if (existing.title != null) inTitle.setText(existing.title);
            inAmt.setText(String.format(Locale.US, "%.2f", existing.defaultAmount));
        }

        new AlertDialog.Builder(activity)
            .setTitle(existing == null ? "Новый шаблон" : "Редактировать шаблон")
            .setView(v)
            .setPositiveButton("Сохранить", (d, w) -> {
                String cat = inCat.getText().toString().trim();
                String title = inTitle.getText().toString().trim();
                double amt;
                try {
                    amt = Double.parseDouble(inAmt.getText().toString().trim().replace(',', '.'));
                    if (amt < 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    Toast.makeText(activity, "Некорректная сумма", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (cat.isEmpty()) {
                    Toast.makeText(activity, "Введите категорию", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (existing == null) {
                    FinanceTemplate t = new FinanceTemplate(cat, title.isEmpty() ? null : title, amt);
                    DbExecutor.runVoid(() -> templateDao.insert(t), this::load);
                } else {
                    existing.category = cat;
                    existing.title = title.isEmpty() ? null : title;
                    existing.defaultAmount = amt;
                    DbExecutor.runVoid(() -> templateDao.update(existing), this::load);
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }

    private static class LoadResult {
        final List<FinanceIncome> incomes;
        final List<FinanceTemplate> templates;
        LoadResult(List<FinanceIncome> i, List<FinanceTemplate> t) { incomes = i; templates = t; }
    }
}
