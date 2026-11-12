package com.example.taskmgr.ui.finance;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.FinanceExpenseDao;
import com.example.taskmgr.data.dao.FinancePeriodDao;
import com.example.taskmgr.model.FinanceExpense;
import com.example.taskmgr.model.FinancePeriod;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FinanceStatsController {

    private final Activity activity;
    private final View root;
    private final FinancePeriodDao periodDao;
    private final FinanceExpenseDao expenseDao;

    private int periodMonths = 6;

    public FinanceStatsController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        this.periodDao = new FinancePeriodDao(activity);
        this.expenseDao = new FinanceExpenseDao(activity);
        setupChips();
        load();
    }

    private void setupChips() {
        ChipGroup group = root.findViewById(R.id.chip_fin_period);
        int[] values = {3, 6, 12};
        String[] labels = {"3 мес", "6 мес", "12 мес"};
        for (int i = 0; i < values.length; i++) {
            Chip chip = new Chip(activity);
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTag(values[i]);
            chip.setChecked(values[i] == periodMonths);
            group.addView(chip);
        }
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = g.findViewById(ids.get(0));
            if (chip == null || chip.getTag() == null) return;
            int newPeriod = (Integer) chip.getTag();
            if (newPeriod != periodMonths) {
                periodMonths = newPeriod;
                load();
            }
        });
    }

    public void load() {
        DbExecutor.run(() -> {
            Calendar from = Calendar.getInstance();
            from.add(Calendar.MONTH, -periodMonths);
            long fromMs = from.getTimeInMillis();
            long toMs = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000;

            List<FinancePeriod> periods = periodDao.getSince(fromMs);
            Map<String, Double> byCategory = expenseDao.getCategoryTotals(fromMs, toMs);
            double total = expenseDao.getTotalSpent(fromMs, toMs);

            // Средний остаток и средняя оценка
            double sumLeftPct = 0;
            int periodsWithIncome = 0;
            List<PeriodRow> periodRows = new ArrayList<>();
            for (FinancePeriod p : periods) {
                List<FinanceExpense> exps = expenseDao.getByPeriod(p.id);
                double spent = 0;
                for (FinanceExpense e : exps) spent += e.amount;
                double left = p.totalIncome - spent;
                double leftPct = p.totalIncome > 0 ? left / p.totalIncome * 100 : 0;
                int score = computeScoreForStats(p.totalIncome, spent, exps);
                if (p.totalIncome > 0) {
                    sumLeftPct += leftPct;
                    periodsWithIncome++;
                }
                PeriodRow row = new PeriodRow();
                row.period = p;
                row.spent = spent;
                row.left = left;
                row.score = score;
                periodRows.add(row);
            }

            double avgLeftPct = periodsWithIncome > 0 ? sumLeftPct / periodsWithIncome : 0;
            double avgScore = 0;
            int countForScore = 0;
            for (PeriodRow r : periodRows) {
                if (r.period.totalIncome > 0) {
                    avgScore += r.score;
                    countForScore++;
                }
            }
            if (countForScore > 0) avgScore /= countForScore;

            return new StatsData(total, byCategory, avgLeftPct, avgScore, periodRows);
        }, data -> {
            if (data == null) return;
            bind(data);
        });
    }

    private int computeScoreForStats(double income, double spent, List<FinanceExpense> exps) {
        if (income <= 0) return 0;
        double left = income - spent;
        double leftPct = left / income * 100.0;
        double score = Math.max(0, Math.min(100, leftPct));
        if (leftPct >= 30) score += 15;
        else if (leftPct >= 20) score += 10;
        double hardSum = 0;
        for (FinanceExpense e : exps) {
            String c = e.category != null ? e.category.toLowerCase(Locale.getDefault()) : "";
            if (c.contains("аренд") || c.contains("жкх") || c.contains("кредит")
                || c.contains("подписк") || c.contains("ипотек")) hardSum += e.amount;
        }
        if (hardSum / income > 0.7) score -= 5;
        score = Math.max(0, Math.min(100, score));
        return (int) Math.round(score);
    }

    private void bind(StatsData data) {
        TextView total = root.findViewById(R.id.fin_stats_total);
        TextView avgLeft = root.findViewById(R.id.fin_stats_avg_left);
        TextView avgScore = root.findViewById(R.id.fin_stats_avg_score);
        LinearLayout catContainer = root.findViewById(R.id.fin_stats_categories);
        LinearLayout periodContainer = root.findViewById(R.id.fin_stats_periods);

        if (data.total <= 0) {
            total.setText("—");
            avgLeft.setText("Нет данных за период");
            avgScore.setText("—");
        } else {
            total.setText(String.format(Locale.getDefault(), "%.2f ₽", data.total));
            avgLeft.setText(String.format(Locale.getDefault(),
                "Средний остаток: %.0f%%", data.avgLeftPct));
            avgScore.setText(String.format(Locale.getDefault(),
                "Средняя оценка: %d/100", Math.round(data.avgScore)));
        }

        // По категориям
        catContainer.removeAllViews();
        if (data.byCategory.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Нет данных");
            empty.setTextColor(ContextCompat.getColor(activity, R.color.text_tertiary));
            empty.setPadding(0, dp(4), 0, dp(4));
            catContainer.addView(empty);
        } else {
            double max = 1;
            for (Double v : data.byCategory.values()) if (v > max) max = v;
            for (Map.Entry<String, Double> e : data.byCategory.entrySet()) {
                catContainer.addView(buildCategoryRow(e.getKey(), e.getValue(), max));
            }
        }

        // По периодам (столбики)
        periodContainer.removeAllViews();
        if (data.periodRows.isEmpty()) return;

        double maxSpent = 1;
        for (PeriodRow r : data.periodRows) if (r.spent > maxSpent) maxSpent = r.spent;

        int maxHeightPx = dp(110);
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM", Locale.getDefault());

        for (PeriodRow r : data.periodRows) {
            LinearLayout col = new LinearLayout(activity);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            View bar = new View(activity);
            int h = (int) (maxHeightPx * (r.spent / maxSpent));
            if (r.spent > 0 && h < dp(3)) h = dp(3);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(16), h);
            barParams.bottomMargin = dp(4);
            bar.setLayoutParams(barParams);

            int color = r.left >= 0
                ? ContextCompat.getColor(activity, R.color.neon_green)
                : ContextCompat.getColor(activity, R.color.semantic_danger);
            bar.setBackgroundColor(color);
            col.addView(bar);

            TextView label = new TextView(activity);
            label.setText(fmt.format(new Date(r.period.startAt)));
            label.setTextSize(9);
            label.setTextColor(ContextCompat.getColor(activity, R.color.text_tertiary));
            label.setGravity(Gravity.CENTER);
            col.addView(label);

            periodContainer.addView(col);
        }
    }

    private View buildCategoryRow(String category, double sum, double max) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(6), 0, dp(6));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);

        TextView name = new TextView(activity);
        name.setText(category);
        name.setTextSize(14);
        name.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
        name.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(name);

        TextView value = new TextView(activity);
        value.setText(String.format(Locale.getDefault(), "%.2f ₽", sum));
        value.setTextSize(13);
        value.setTextColor(ContextCompat.getColor(activity, R.color.neon_green));
        top.addView(value);
        wrapper.addView(top);

        ProgressBar bar = new ProgressBar(activity, null,
            android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        barLp.topMargin = dp(4);
        bar.setLayoutParams(barLp);
        bar.setMax(100);
        bar.setProgress((int) Math.round(100.0 * sum / max));
        bar.setProgressTintList(ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.neon_green)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.surface_variant)));
        wrapper.addView(bar);

        return wrapper;
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }

    private static class StatsData {
        final double total;
        final Map<String, Double> byCategory;
        final double avgLeftPct;
        final double avgScore;
        final List<PeriodRow> periodRows;

        StatsData(double t, Map<String, Double> bc, double al, double as, List<PeriodRow> pr) {
            total = t; byCategory = bc; avgLeftPct = al; avgScore = as; periodRows = pr;
        }
    }

    private static class PeriodRow {
        FinancePeriod period;
        double spent;
        double left;
        int score;
    }
}
