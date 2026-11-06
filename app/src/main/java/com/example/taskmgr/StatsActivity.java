package com.example.taskmgr;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.taskmgr.dao.StatsDao;
import com.example.taskmgr.utill.DbExecutor;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class StatsActivity extends AppCompatActivity {

    private static final int PERIOD_ALL = 0;
    private static final int PERIOD_7 = 7;
    private static final int PERIOD_30 = 30;
    private static final int PERIOD_90 = 90;

    private int currentPeriodDays = PERIOD_ALL;

    private StatsDao statsDao;

    private ChipGroup periodGroup;
    private TextView statTotal, statActive, statDone, statPercent, statOverdue;
    private ProgressBar statPercentBar;
    private LinearLayout priorityContainer;
    private LinearLayout sectionContainer;
    private View cardPeriod;
    private TextView periodTitle, periodCompletions, periodAvg;
    private TextView shoppingLine, shoppingSum;

    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        statsDao = new StatsDao(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Статистика");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        statTotal = findViewById(R.id.stat_total);
        statActive = findViewById(R.id.stat_active);
        statDone = findViewById(R.id.stat_done);
        statPercent = findViewById(R.id.stat_percent);
        statPercentBar = findViewById(R.id.stat_percent_bar);
        statOverdue = findViewById(R.id.stat_overdue);
        priorityContainer = findViewById(R.id.priority_container);
        sectionContainer = findViewById(R.id.section_container);
        cardPeriod = findViewById(R.id.card_period);
        periodTitle = findViewById(R.id.period_title);
        periodCompletions = findViewById(R.id.period_completions);
        periodAvg = findViewById(R.id.period_avg);
        shoppingLine = findViewById(R.id.shopping_line);
        shoppingSum = findViewById(R.id.shopping_sum);

        setupPeriodChips();
        loadStats();
    }

    private void setupPeriodChips() {
        periodGroup = findViewById(R.id.period_chip_group);

        int[] values = {PERIOD_ALL, PERIOD_7, PERIOD_30, PERIOD_90};
        String[] labels = {"Всё время", "7 дней", "30 дней", "90 дней"};

        for (int i = 0; i < values.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTag(values[i]);
            chip.setChecked(values[i] == currentPeriodDays);
            periodGroup.addView(chip);
        }

        periodGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = group.findViewById(checkedIds.get(0));
            if (chip == null || chip.getTag() == null) return;
            int newPeriod = (Integer) chip.getTag();
            if (newPeriod != currentPeriodDays) {
                currentPeriodDays = newPeriod;
                loadStats();
            }
        });
    }

    private void loadStats() {
        if (loading) return;
        loading = true;
        final int periodDays = currentPeriodDays;

        DbExecutor.run(() -> {
            StatsDao.Summary summary = statsDao.getSummary();
            List<StatsDao.PriorityRow> priorities = statsDao.getActiveByPriority();
            List<StatsDao.SectionRow> sections = statsDao.getBySection();
            StatsDao.PeriodStats period = periodDays > 0
                    ? statsDao.getCompletionsForPeriod(periodDays)
                    : null;
            StatsDao.ShoppingStats shopping = statsDao.getShoppingStats();

            Object[] out = new Object[5];
            out[0] = summary;
            out[1] = priorities;
            out[2] = sections;
            out[3] = period;
            out[4] = shopping;
            return out;
        }, result -> {
            loading = false;
            if (result == null) return;
            Object[] arr = (Object[]) result;
            StatsDao.Summary summary = (StatsDao.Summary) arr[0];
            @SuppressWarnings("unchecked")
            List<StatsDao.PriorityRow> priorities = (List<StatsDao.PriorityRow>) arr[1];
            @SuppressWarnings("unchecked")
            List<StatsDao.SectionRow> sections = (List<StatsDao.SectionRow>) arr[2];
            StatsDao.PeriodStats period = (StatsDao.PeriodStats) arr[3];
            StatsDao.ShoppingStats shopping = (StatsDao.ShoppingStats) arr[4];

            bindSummary(summary);
            bindPriorities(priorities);
            bindSections(sections);
            bindPeriod(period, periodDays);
            bindShopping(shopping);
        });
    }

    private void bindSummary(StatsDao.Summary s) {
        statTotal.setText(String.valueOf(s.total));
        statActive.setText(String.valueOf(s.active));
        statDone.setText(String.valueOf(s.done));
        int pct = s.percentDone();
        statPercent.setText("Завершено: " + pct + "%");
        statPercentBar.setProgress(pct);
        statOverdue.setText("Просрочено сейчас: " + s.overdue);
    }

    private void bindPriorities(List<StatsDao.PriorityRow> rows) {
        priorityContainer.removeAllViews();
        // Сортируем: important, normal, whatever
        String[] order = {"important", "normal", "whatever"};
        int max = 1;
        for (StatsDao.PriorityRow r : rows) if (r.active > max) max = r.active;

        LayoutInflater inf = LayoutInflater.from(this);
        for (String p : order) {
            StatsDao.PriorityRow row = null;
            for (StatsDao.PriorityRow r : rows) if (p.equals(r.priority)) { row = r; break; }
            if (row == null) continue;

            View v = inf.inflate(R.layout.item_stats_priority, priorityContainer, false);
            TextView name = v.findViewById(R.id.prio_name);
            TextView count = v.findViewById(R.id.prio_count);
            ProgressBar bar = v.findViewById(R.id.prio_bar);

            name.setText(priorityLabel(p));
            count.setText(String.valueOf(row.active));
            bar.setProgress((int) Math.round(100.0 * row.active / max));
            priorityContainer.addView(v);
        }
    }

    private String priorityLabel(String p) {
        switch (p) {
            case "important": return "Важно";
            case "whatever":  return "Потом";
            default:          return "Обычно";
        }
    }

    private void bindSections(List<StatsDao.SectionRow> rows) {
        sectionContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (StatsDao.SectionRow r : rows) {
            View v = inf.inflate(R.layout.item_stats_section, sectionContainer, false);
            TextView name = v.findViewById(R.id.row_name);
            TextView counts = v.findViewById(R.id.row_counts);
            TextView percent = v.findViewById(R.id.row_percent);
            ProgressBar bar = v.findViewById(R.id.row_bar);

            name.setText(r.name);
            counts.setText(r.active + "/" + (r.active + r.done));
            int pct = r.percentDone();
            percent.setText(pct + "%");
            bar.setProgress(pct);
            sectionContainer.addView(v);
        }
    }

    private void bindPeriod(StatsDao.PeriodStats p, int periodDays) {
        if (periodDays <= 0 || p == null) {
            cardPeriod.setVisibility(View.GONE);
            return;
        }
        cardPeriod.setVisibility(View.VISIBLE);
        periodTitle.setText("Выполнено за " + periodDays + " дн.");
        periodCompletions.setText(String.valueOf(p.completions));
        periodAvg.setText(String.format("В среднем: %.1f/день", p.perDay()));
    }

    private void bindShopping(StatsDao.ShoppingStats s) {
        shoppingLine.setText("Всего: " + s.total + " • Куплено: " + s.bought
                + " • Не куплено: " + s.notBought);
        shoppingSum.setText(String.format("Сумма некупленного: %.2f", s.notBoughtSum));
    }
}