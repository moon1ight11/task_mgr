package com.example.taskmgr.ui.utilities;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.taskmgr.R;
import com.example.taskmgr.data.DatabaseHelper;
import com.example.taskmgr.data.dao.UtilityPeriodDao;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UtilitiesStatsController {

    private final Activity activity;
    private final View root;
    private final UtilityPeriodDao periodDao;
    private final DatabaseHelper dbHelper;

    private int periodMonths = 6;
    private String selectedHouse = null;
    private List<String> houses = new ArrayList<>();
    private boolean spinnerReady = false;

    public UtilitiesStatsController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        this.periodDao = new UtilityPeriodDao(activity);
        this.dbHelper = DatabaseHelper.getInstance(activity);

        setupPeriodChips();
        setupHouseSpinner();
        load();
    }

    private void setupPeriodChips() {
        ChipGroup group = root.findViewById(R.id.chip_period);
        int[] values = {6, 12, 24};
        String[] labels = {"6 мес", "12 мес", "24 мес"};

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

    private void setupHouseSpinner() {
        Spinner spinner = root.findViewById(R.id.spinner_house);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                if (!spinnerReady) return;
                String chosen = pos == 0 ? null : houses.get(pos - 1);
                if ((chosen == null && selectedHouse != null)
                    || (chosen != null && !chosen.equals(selectedHouse))) {
                    selectedHouse = chosen;
                    load();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    public void load() {
        DbExecutor.run(() -> {
            List<String> houses = periodDao.getHouses();
            StatsData data = queryStats(periodMonths, selectedHouse);
            return new LoadResult(houses, data);
        }, res -> {
            if (res == null) return;
            houses = res.houses;
            updateHouseSpinner();
            bind(res.data);
        });
    }

    private StatsData queryStats(int months, String houseFilter) {
        StatsData s = new StatsData();
        s.months = months;

        // Вычисляем окно в Java: [fromYear/fromMonth; текущий месяц]
        Calendar from = Calendar.getInstance();
        from.add(Calendar.MONTH, -(months - 1));
        int fromYear = from.get(Calendar.YEAR);
        int fromMonth = from.get(Calendar.MONTH); // 0..11
        Calendar now = Calendar.getInstance();
        int toYear = now.get(Calendar.YEAR);
        int toMonth = now.get(Calendar.MONTH);

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Условие по окну (period_month тоже 0..11 как в Calendar)
        String window = "(p.period_year > ? OR (p.period_year = ? AND p.period_month >= ?)) " +
            "AND (p.period_year < ? OR (p.period_year = ? AND p.period_month <= ?))";
        List<String> baseArgs = new ArrayList<>();
        baseArgs.add(String.valueOf(fromYear));
        baseArgs.add(String.valueOf(fromYear));
        baseArgs.add(String.valueOf(fromMonth));
        baseArgs.add(String.valueOf(toYear));
        baseArgs.add(String.valueOf(toYear));
        baseArgs.add(String.valueOf(toMonth));

        String houseCond = "";
        if (houseFilter != null) {
            houseCond = " AND p.house = ?";
            baseArgs.add(houseFilter);
        }
        String where = " WHERE " + window + houseCond;

        // 1) Всего + месяцев с данными
        String totalSql = "SELECT SUM(r.amount), " +
            "COUNT(DISTINCT p.period_year || '-' || p.period_month) " +
            "FROM utility_readings r JOIN utility_periods p ON p.id = r.period_id" + where;
        try (Cursor c = db.rawQuery(totalSql, baseArgs.toArray(new String[0]))) {
            if (c.moveToFirst()) {
                s.total = c.isNull(0) ? 0 : c.getDouble(0);
                s.monthCount = c.isNull(1) ? 0 : c.getInt(1);
            }
        }

        // 2) По счётчикам
        String byMeterSql = "SELECT r.meter_id, m.name, SUM(r.amount) " +
            "FROM utility_readings r " +
            "JOIN utility_periods p ON p.id = r.period_id " +
            "JOIN utility_meters m ON m.id = r.meter_id" + where +
            " GROUP BY r.meter_id, m.name ORDER BY 3 DESC";
        try (Cursor c = db.rawQuery(byMeterSql, baseArgs.toArray(new String[0]))) {
            while (c.moveToNext()) {
                MeterRow r = new MeterRow();
                r.meterId = c.getLong(0);
                r.name = c.getString(1);
                r.sum = c.getDouble(2);
                s.byMeter.add(r);
            }
        }

        // 3) По месяцам
        Map<String, Double> byMonth = new HashMap<>();
        String byMonthSql = "SELECT p.period_year, p.period_month, SUM(r.amount) " +
            "FROM utility_readings r JOIN utility_periods p ON p.id = r.period_id" + where +
            " GROUP BY p.period_year, p.period_month";
        try (Cursor c = db.rawQuery(byMonthSql, baseArgs.toArray(new String[0]))) {
            while (c.moveToNext()) {
                byMonth.put(c.getInt(0) + "-" + c.getInt(1), c.getDouble(2));
            }
        }

        // Заполняем нулями весь диапазон
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -(months - 1));
        for (int i = 0; i < months; i++) {
            int y = cal.get(Calendar.YEAR);
            int m = cal.get(Calendar.MONTH);
            MonthRow row = new MonthRow();
            row.year = y;
            row.month = m;
            row.sum = byMonth.getOrDefault(y + "-" + m, 0.0);
            s.byMonth.add(row);
            cal.add(Calendar.MONTH, 1);
        }

        return s;
    }

    private void updateHouseSpinner() {
        Spinner spinner = root.findViewById(R.id.spinner_house);
        List<String> items = new ArrayList<>();
        items.add("Все дома");
        items.addAll(houses);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int selected = 0;
        if (selectedHouse != null) {
            int idx = houses.indexOf(selectedHouse);
            if (idx >= 0) selected = idx + 1;
        }
        spinner.setSelection(selected, false);
        spinnerReady = true;
    }

    private void bind(StatsData s) {
        TextView total = root.findViewById(R.id.stats_total);
        TextView avg = root.findViewById(R.id.stats_avg);

        if (s.total <= 0) {
            total.setText("—");
            avg.setText("Нет данных за выбранный период");
        } else {
            total.setText(String.format(Locale.getDefault(), "%.2f ₽", s.total));
            double perMonth = s.monthCount > 0 ? s.total / s.monthCount : 0;
            avg.setText(String.format(Locale.getDefault(),
                "В среднем: %.2f ₽/мес · месяцев с данными: %d",
                perMonth, s.monthCount));
        }

        LinearLayout byMeter = root.findViewById(R.id.container_by_meter);
        byMeter.removeAllViews();
        if (s.byMeter.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Нет данных");
            empty.setTextColor(ContextCompat.getColor(activity, R.color.text_tertiary));
            empty.setPadding(0, dp(4), 0, dp(4));
            byMeter.addView(empty);
        } else {
            double max = 1;
            for (MeterRow r : s.byMeter) if (r.sum > max) max = r.sum;
            for (MeterRow r : s.byMeter) {
                byMeter.addView(buildMeterRow(r, s.total, max));
            }
        }

        LinearLayout byMonth = root.findViewById(R.id.container_by_month);
        byMonth.removeAllViews();
        if (s.byMonth.isEmpty()) return;

        double maxSum = 1;
        for (MonthRow r : s.byMonth) if (r.sum > maxSum) maxSum = r.sum;

        int maxHeightPx = dp(110);
        SimpleDateFormat fmt = new SimpleDateFormat("LLL", new Locale("ru"));

        for (MonthRow r : s.byMonth) {
            LinearLayout col = new LinearLayout(activity);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            View bar = new View(activity);
            int h = (int) (maxHeightPx * (r.sum / maxSum));
            if (r.sum > 0 && h < dp(3)) h = dp(3);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(20), h);
            barParams.bottomMargin = dp(4);
            bar.setLayoutParams(barParams);
            bar.setBackgroundColor(r.sum > 0
                ? ContextCompat.getColor(activity, R.color.neon_green)
                : ContextCompat.getColor(activity, R.color.divider_strong));
            col.addView(bar);

            Calendar cal = Calendar.getInstance();
            cal.set(r.year, r.month, 1);
            TextView label = new TextView(activity);
            label.setText(fmt.format(cal.getTime()).replace(".", ""));
            label.setTextSize(9);
            label.setTextColor(ContextCompat.getColor(activity, R.color.text_tertiary));
            label.setGravity(Gravity.CENTER);
            col.addView(label);

            byMonth.addView(col);
        }
    }

    private View buildMeterRow(MeterRow r, double total, double max) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(6), 0, dp(6));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);

        TextView name = new TextView(activity);
        name.setText(r.name);
        name.setTextSize(14);
        name.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
        name.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(name);

        TextView sum = new TextView(activity);
        sum.setText(String.format(Locale.getDefault(), "%.2f ₽", r.sum));
        sum.setTextSize(13);
        sum.setTextColor(ContextCompat.getColor(activity, R.color.neon_green));
        top.addView(sum);
        wrapper.addView(top);

        ProgressBar bar = new ProgressBar(activity, null,
            android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        barLp.topMargin = dp(4);
        bar.setLayoutParams(barLp);
        bar.setMax(100);
        bar.setProgress((int) Math.round(100.0 * r.sum / max));
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
        int months;
        double total;
        int monthCount;
        List<MeterRow> byMeter = new ArrayList<>();
        List<MonthRow> byMonth = new ArrayList<>();
    }

    private static class MeterRow {
        long meterId;
        String name;
        double sum;
    }

    private static class MonthRow {
        int year;
        int month;
        double sum;
    }

    private static class LoadResult {
        final List<String> houses;
        final StatsData data;
        LoadResult(List<String> h, StatsData d) { houses = h; data = d; }
    }
}
