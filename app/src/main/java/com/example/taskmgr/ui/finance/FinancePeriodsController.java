package com.example.taskmgr.ui.finance;

import android.app.Activity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.FinanceExpenseDao;
import com.example.taskmgr.data.dao.FinanceIncomeDao;
import com.example.taskmgr.data.dao.FinancePeriodDao;
import com.example.taskmgr.data.dao.FinanceTemplateDao;
import com.example.taskmgr.model.FinanceExpense;
import com.example.taskmgr.model.FinanceIncome;
import com.example.taskmgr.model.FinancePeriod;
import com.example.taskmgr.model.FinanceTemplate;
import com.example.taskmgr.util.DbExecutor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FinancePeriodsController {

    private final Activity activity;
    private final View root;
    private final FinanceIncomeDao incomeDao;
    private final FinancePeriodDao periodDao;
    private final FinanceExpenseDao expenseDao;
    private final FinanceTemplateDao templateDao;
    private final FinanceExpensesAdapter adapter;

    private List<FinancePeriod> periods = new ArrayList<>();
    private final List<FinanceTemplate> templatesForPick = new ArrayList<>();
    private final java.util.Set<Long> pickedTemplateIds = new java.util.HashSet<>();
    private int currentIndex = 0;
    private FinancePeriod currentPeriod;

    public FinancePeriodsController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        incomeDao = new FinanceIncomeDao(activity);
        periodDao = new FinancePeriodDao(activity);
        expenseDao = new FinanceExpenseDao(activity);
        templateDao = new FinanceTemplateDao(activity);

        RecyclerView recycler = root.findViewById(R.id.fin_expenses_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new FinanceExpensesAdapter(this::showExpenseActions);
        recycler.setAdapter(adapter);

        root.findViewById(R.id.btn_prev_period).setOnClickListener(v -> {
            if (currentIndex > 0) { currentIndex--; bindCurrentPeriod(); }
        });
        root.findViewById(R.id.btn_next_period).setOnClickListener(v -> {
            if (currentIndex < periods.size() - 1) { currentIndex++; bindCurrentPeriod(); }
        });
        root.findViewById(R.id.btn_fin_add_expense).setOnClickListener(v -> showAddExpenseDialog());
        root.findViewById(R.id.btn_fin_add_from_template)
            .setOnClickListener(v -> showPickTemplatesDialog());
    }

    public void load() {
        DbExecutor.run(() -> {
            ensurePeriodsGenerated();
            List<FinancePeriod> list = periodDao.getAll();
            int idx = 0;
            long now = System.currentTimeMillis();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).startAt <= now && list.get(i).endAt > now) { idx = i; break; }
            }
            return new LoadResult(list, idx);
        }, res -> {
            if (res == null) return;
            periods = res.periods;
            currentIndex = res.index;
            bindCurrentPeriod();
        });
    }

    public void showPickTemplatesDialog() {
        if (currentPeriod == null) {
            Toast.makeText(activity, "Сначала выберите период", Toast.LENGTH_SHORT).show();
            return;
        }
        DbExecutor.run(
            (java.util.concurrent.Callable<List<FinanceTemplate>>) () -> templateDao.getAll(true),
            (List<FinanceTemplate> list) -> {
                if (list == null || list.isEmpty()) {
                    Toast.makeText(activity, "Шаблонов нет. Добавьте их в «Настройках»",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                templatesForPick.clear();
                templatesForPick.addAll(list);
                pickedTemplateIds.clear();

                View v = activity.getLayoutInflater()
                    .inflate(R.layout.dialog_finance_templates_pick, null);
                LinearLayout container = v.findViewById(R.id.container_template_pick);

                for (FinanceTemplate t : templatesForPick) {
                    CheckBox cb = new CheckBox(activity);
                    String catPart = t.category;
                    String subPart = (t.title != null && !t.title.isEmpty()) ? " · " + t.title : "";
                    String amountPart = String.format(Locale.getDefault(), " — %.2f ₽", t.defaultAmount);
                    android.text.SpannableString sp = new android.text.SpannableString(
                        catPart + subPart + amountPart);
                    int catEnd = catPart.length();
                    sp.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        0, catEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int amountStart = sp.length() - amountPart.length();
                    sp.setSpan(new android.text.style.ForegroundColorSpan(
                            ContextCompat.getColor(activity, R.color.neon_green)),
                        amountStart, sp.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    cb.setText(sp);
                    cb.setTextSize(14);
                    cb.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
                    cb.setPadding(0, dp(6), 0, dp(6));
                    long id = t.id;
                    cb.setOnCheckedChangeListener((b, checked) -> {
                        if (checked) pickedTemplateIds.add(id);
                        else pickedTemplateIds.remove(id);
                    });
                    container.addView(cb);
                }

                new AlertDialog.Builder(activity)
                    .setTitle("Добавить из шаблона")
                    .setView(v)
                    .setPositiveButton("Добавить", (d, w) -> {
                        if (pickedTemplateIds.isEmpty()) return;
                        List<FinanceTemplate> chosen = new ArrayList<>();
                        for (FinanceTemplate t : templatesForPick) {
                            if (pickedTemplateIds.contains(t.id)) chosen.add(t);
                        }
                        final long pid = currentPeriod.id;
                        DbExecutor.runVoid(() -> {
                            long now = System.currentTimeMillis();
                            for (FinanceTemplate t : chosen) {
                                FinanceExpense e = new FinanceExpense();
                                e.periodId = pid;
                                e.category = t.category;
                                e.title = t.title;
                                e.amount = t.defaultAmount;
                                e.createdAt = now++;
                                e.sourceType = "manual";
                                expenseDao.insert(e);
                            }
                        }, this::bindCurrentPeriod);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            }
        );
    }

    private void bindCurrentPeriod() {
        TextView tvName = root.findViewById(R.id.tv_period_name);
        TextView tvRange = root.findViewById(R.id.tv_fin_period_range);
        TextView tvIncome = root.findViewById(R.id.tv_fin_income);
        TextView tvSpent = root.findViewById(R.id.tv_fin_spent);
        TextView tvLeft = root.findViewById(R.id.tv_fin_left);
        TextView empty = root.findViewById(R.id.tv_fin_empty);
        RecyclerView recycler = root.findViewById(R.id.fin_expenses_recycler);
        View card = root.findViewById(R.id.card_fin_summary);

        if (periods.isEmpty() || currentIndex < 0 || currentIndex >= periods.size()) {
            currentPeriod = null;
            tvName.setText("—");
            card.setVisibility(View.GONE);
            empty.setText("Нет периодов. Добавьте поступления в настройках.");
            empty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            adapter.setItems(new ArrayList<>());
            return;
        }

        currentPeriod = periods.get(currentIndex);
        card.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);

        tvName.setText(currentPeriod.name);

        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        tvRange.setText(df.format(new Date(currentPeriod.startAt)) + " — "
            + df.format(new Date(currentPeriod.endAt)));

        tvIncome.setText(String.format(Locale.getDefault(), "Доход: %.2f ₽", currentPeriod.totalIncome));

        final long pid = currentPeriod.id;
        DbExecutor.run(
            (java.util.concurrent.Callable<List<FinanceExpense>>) () -> expenseDao.getByPeriod(pid),
            (List<FinanceExpense> list) -> {
                if (list == null) list = new ArrayList<>();
                double spent = 0;
                for (FinanceExpense e : list) spent += e.amount;
                double left = currentPeriod.totalIncome - spent;
                tvSpent.setText(String.format(Locale.getDefault(), "Потрачено: %.2f ₽", spent));
                tvLeft.setText(String.format(Locale.getDefault(), "Остаток: %.2f ₽", left));
                tvLeft.setTextColor(ContextCompat.getColor(activity,
                    left >= 0 ? R.color.neon_green : R.color.semantic_danger));
                adapter.setItems(list);
            }
        );
    }

    public void showAddExpenseDialog() {
        if (currentPeriod == null) {
            Toast.makeText(activity, "Сначала настройте поступления во вкладке «Настройки»",
                Toast.LENGTH_LONG).show();
            return;
        }
        showExpenseDialog(null);
    }

    private void showExpenseActions(FinanceExpense e) {
        new AlertDialog.Builder(activity)
            .setTitle(e.category)
            .setItems(new String[]{"Редактировать", "Удалить"}, (d, w) -> {
                if (w == 0) showExpenseDialog(e);
                else {
                    DbExecutor.runVoid(() -> expenseDao.delete(e.id), this::bindCurrentPeriod);
                }
            })
            .show();
    }

    private void showExpenseDialog(FinanceExpense existing) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_finance_expense, null);
        EditText inCat = v.findViewById(R.id.input_exp_category);
        EditText inTitle = v.findViewById(R.id.input_exp_title);
        EditText inAmt = v.findViewById(R.id.input_exp_amount);

        if (existing != null) {
            inCat.setText(existing.category);
            if (existing.title != null) inTitle.setText(existing.title);
            inAmt.setText(String.format(Locale.US, "%.2f", existing.amount));
        }

        new AlertDialog.Builder(activity)
            .setTitle(existing == null ? "Новая трата" : "Редактировать трату")
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
                FinanceExpense e = existing != null ? existing : new FinanceExpense();
                e.periodId = currentPeriod.id;
                e.category = cat;
                e.title = title.isEmpty() ? null : title;
                e.amount = amt;
                if (existing == null) {
                    e.createdAt = System.currentTimeMillis();
                    e.sourceType = "manual";
                }
                DbExecutor.runVoid(
                    () -> { if (existing == null) expenseDao.insert(e); else expenseDao.update(e); },
                    this::bindCurrentPeriod
                );
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    // ---- генерация периодов ----

    private void ensurePeriodsGenerated() {
        List<FinanceIncome> incomes = incomeDao.getAll(true);
        if (incomes.isEmpty()) return;

        List<Integer> days = new ArrayList<>();
        for (FinanceIncome i : incomes) {
            if (!days.contains(i.dayOfMonth)) days.add(i.dayOfMonth);
        }
        days.sort(Integer::compareTo);

        Calendar now = Calendar.getInstance();
        for (int m = 0; m < 6; m++) {
            Calendar month = (Calendar) now.clone();
            month.add(Calendar.MONTH, m);
            int year = month.get(Calendar.YEAR);
            int mon = month.get(Calendar.MONTH);
            int maxDay = month.getActualMaximum(Calendar.DAY_OF_MONTH);

            List<Long> startDates = new ArrayList<>();
            for (int day : days) {
                int d = Math.min(day, maxDay);
                Calendar c = Calendar.getInstance();
                c.set(year, mon, d, 0, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                startDates.add(c.getTimeInMillis());
            }
            startDates.sort(Long::compareTo);

            for (int i = 0; i < startDates.size(); i++) {
                long startAt = startDates.get(i);
                long endAt;
                if (i + 1 < startDates.size()) {
                    endAt = startDates.get(i + 1);
                } else {
                    Calendar next = Calendar.getInstance();
                    next.set(year, mon, 1, 0, 0, 0);
                    next.set(Calendar.MILLISECOND, 0);
                    next.add(Calendar.MONTH, 1);
                    int nextYear = next.get(Calendar.YEAR);
                    int nextMonth = next.get(Calendar.MONTH);
                    int nextMaxDay = next.getActualMaximum(Calendar.DAY_OF_MONTH);
                    int firstDay = Math.min(days.get(0), nextMaxDay);
                    Calendar c = Calendar.getInstance();
                    c.set(nextYear, nextMonth, firstDay, 0, 0, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    endAt = c.getTimeInMillis();
                }
                if (periodDao.findByStart(startAt) != null) continue;

                double sum = 0;
                for (FinanceIncome inc : incomes) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(startAt);
                    if (c.get(Calendar.DAY_OF_MONTH) == Math.min(inc.dayOfMonth, maxDay)) {
                        sum += inc.amount;
                    }
                }

                String name = buildPeriodName(startAt, endAt);
                FinancePeriod p = new FinancePeriod(startAt, endAt, name, sum);
                periodDao.insert(p);
            }
        }
    }

    private String buildPeriodName(long startAt, long endAt) {
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return df.format(new Date(startAt)) + " — " + df.format(new Date(endAt));
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }

    private static class LoadResult {
        final List<FinancePeriod> periods;
        final int index;
        LoadResult(List<FinancePeriod> p, int i) { periods = p; index = i; }
    }
}
