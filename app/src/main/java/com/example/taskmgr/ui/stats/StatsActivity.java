package com.example.taskmgr.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.StatsDao;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.Locale;

public class StatsActivity extends AppCompatActivity {

    private static final int PERIOD_ALL = 0;
    private static final int PERIOD_7 = 7;
    private static final int PERIOD_30 = 30;
    private static final int PERIOD_90 = 90;

    private int currentPeriodDays = PERIOD_ALL;

    private TextView statTotal, statActive, statDone, statPercent, statOverdue;
    private ProgressBar statPercentBar;
    private LinearLayout priorityContainer;
    private LinearLayout sectionContainer;
    private View cardPeriod;
    private TextView periodTitle, periodCompletions, periodAvg;
    private TextView statAvgTime, statAvgTimeMeta;
    private TextView statOnTime, statOnTimeMeta;
    private ProgressBar statOnTimeBar;
    private TextView shoppingLine, shoppingSum;

    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

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
        statAvgTime = findViewById(R.id.stat_avg_time);
        statAvgTimeMeta = findViewById(R.id.stat_avg_time_meta);
        statOnTime = findViewById(R.id.stat_on_time);
        statOnTimeMeta = findViewById(R.id.stat_on_time_meta);
        statOnTimeBar = findViewById(R.id.stat_on_time_bar);
        shoppingLine = findViewById(R.id.shopping_line);
        shoppingSum = findViewById(R.id.shopping_sum);

        setupPeriodChips();
        loadStats();
    }

    private void setupPeriodChips() {
        ChipGroup periodGroup = findViewById(R.id.period_chip_group);

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
        final StatsDao statsDao = new StatsDao(this);

        DbExecutor.run(() -> {
            StatsDao.Summary summary = statsDao.getSummary();
            List<StatsDao.PriorityRow> priorities = statsDao.getActiveByPriority();
            List<StatsDao.SectionRow> sections = statsDao.getBySection();
            StatsDao.PeriodStats period = periodDays > 0
                ? statsDao.getCompletionsForPeriod(periodDays)
                : null;
            StatsDao.ShoppingStats shopping = statsDao.getShoppingStats();
            StatsDao.CompletionTimeStats timeStats = statsDao.getCompletionTimeStats(periodDays);
            StatsDao.DeadlineStats deadlineStats = statsDao.getDeadlineStats(periodDays);

            return new LoadResult(summary, priorities, sections, period,
                shopping, timeStats, deadlineStats);
        }, result -> {
            loading = false;
            if (result == null) return;

            bindSummary(result.summary());
            bindPriorities(result.priorities());
            bindSections(result.sections());
            bindPeriod(result.period(), periodDays);
            bindShopping(result.shopping());
            bindCompletionTime(result.timeStats());
            bindDeadline(result.deadlineStats());
        });
    }

    @SuppressWarnings("SetTextI18n")
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
        String[] order = {"important", "normal", "whatever"};
        int max = 1;
        for (StatsDao.PriorityRow r : rows) if (r.active > max) max = r.active;

        LayoutInflater inf = LayoutInflater.from(this);
        for (String p : order) {
            StatsDao.PriorityRow row = null;
            for (StatsDao.PriorityRow r : rows)
                if (p.equals(r.priority)) {
                    row = r;
                    break;
                }
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
        return switch (p) {
            case "important" -> "Важно";
            case "whatever" -> "Потом";
            default -> "Обычно";
        };
    }

    @SuppressWarnings("SetTextI18n")
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

    private void bindCompletionTime(StatsDao.CompletionTimeStats s) {
        if (s == null || !s.hasData()) {
            statAvgTime.setText("—");
            statAvgTimeMeta.setText("Нет данных за период");
            return;
        }
        statAvgTime.setText(formatDuration((long) s.avgMillis));
        String meta = String.format(Locale.getDefault(),
            "Мин: %s • Макс: %s • Всего: %d",
            formatDuration(s.minMillis),
            formatDuration(s.maxMillis),
            s.count);
        statAvgTimeMeta.setText(meta);
    }

    @SuppressWarnings("SetTextI18n")
    private void bindDeadline(StatsDao.DeadlineStats s) {
        if (s == null || !s.hasData()) {
            statOnTime.setText("—");
            statOnTimeBar.setProgress(0);
            statOnTimeMeta.setText("Нет задач с дедлайном за период");
            return;
        }
        int pct = s.percentOnTime();
        statOnTime.setText(pct + "%");
        statOnTimeBar.setProgress(pct);
        statOnTimeMeta.setText(String.format(Locale.getDefault(),
            "В срок: %d • С опозданием: %d • Всего: %d",
            s.onTime, s.late, s.completedWithDeadline));
    }

    private String formatDuration(long millis) {
        if (millis <= 0) return "0м";
        long sec = millis / 1000;
        long min = sec / 60;
        long hour = min / 60;
        long day = hour / 24;

        if (day > 0) {
            return day + "д " + (hour % 24) + "ч";
        }
        if (hour > 0) {
            return hour + "ч " + (min % 60) + "м";
        }
        if (min > 0) {
            return min + "м";
        }
        return sec + "с";
    }

    @SuppressWarnings("SetTextI18n")
    private void bindPeriod(StatsDao.PeriodStats p, int periodDays) {
        if (periodDays <= 0 || p == null) {
            cardPeriod.setVisibility(View.GONE);
            return;
        }
        cardPeriod.setVisibility(View.VISIBLE);
        periodTitle.setText("Выполнено за " + periodDays + " дн.");
        periodCompletions.setText(String.valueOf(p.completions));
        periodAvg.setText(String.format(Locale.getDefault(), "В среднем: %.1f/день", p.perDay()));
    }

    @SuppressWarnings("SetTextI18n")
    private void bindShopping(StatsDao.ShoppingStats s) {
        shoppingLine.setText("Всего: " + s.total + " • Куплено: " + s.bought
            + " • Не куплено: " + s.notBought);
        shoppingSum.setText(String.format(Locale.getDefault(), "Сумма к покупке: %.2f", s.notBoughtSum));
    }

    private record LoadResult(
        StatsDao.Summary summary,
        List<StatsDao.PriorityRow> priorities,
        List<StatsDao.SectionRow> sections,
        StatsDao.PeriodStats period,
        StatsDao.ShoppingStats shopping,
        StatsDao.CompletionTimeStats timeStats,
        StatsDao.DeadlineStats deadlineStats) {
    }
}
