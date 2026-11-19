package com.example.taskmgr.ui.smoking;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.SmokingDao;
import com.example.taskmgr.model.SmokingEntry;
import com.example.taskmgr.service.SmokingFacts;
import com.example.taskmgr.service.SmokingStats;
import com.example.taskmgr.util.DbExecutor;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmokingActivity extends AppCompatActivity {

    private final SimpleDateFormat dateTimeFormat =
        new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    private SmokingDao dao;
    private TextView tvDaysFree;
    private TextView tvDaysFreeLabel;
    private TextView tvLastSmoked;
    private TextView tvToday;
    private TextView tvTotal;
    private TextView tvAvg;
    private LinearLayout factsContainer;
    private TextView factsEmpty;
    private LinearLayout historyContainer;
    private TextView historyEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smoking);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Отказ от курения");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        dao = new SmokingDao(this);

        tvDaysFree = findViewById(R.id.tv_days_free);
        tvDaysFreeLabel = findViewById(R.id.tv_days_free_label);
        tvLastSmoked = findViewById(R.id.tv_last_smoked);
        tvToday = findViewById(R.id.tv_today);
        tvTotal = findViewById(R.id.tv_total);
        tvAvg = findViewById(R.id.tv_avg);
        factsContainer = findViewById(R.id.facts_container);
        factsEmpty = findViewById(R.id.facts_empty);
        historyContainer = findViewById(R.id.history_container);
        historyEmpty = findViewById(R.id.history_empty);

        findViewById(R.id.btn_smoke_now).setOnClickListener(v ->
            addEntry(1, System.currentTimeMillis()));
        findViewById(R.id.btn_add_entry).setOnClickListener(v -> showAddDialog());

        load();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_smoking, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_smoking) {
            confirmClearAll();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void load() {
        DbExecutor.run(
            () -> new LoadData(dao.getStats(), dao.getRecent(50)),
            data -> {
                if (data == null) return;
                bindStats(data.stats);
                bindFacts(data.stats);
                bindHistory(data.entries);
            });
    }

    @SuppressWarnings("SetTextI18n")
    private void bindStats(SmokingStats stats) {
        tvDaysFree.setText(String.valueOf(stats.hasData() ? stats.daysWithout : 0));
        tvDaysFreeLabel.setText(daysLabel(stats.hasData() ? stats.daysWithout : 0));
        tvToday.setText(String.valueOf(stats.todaySmoked));
        tvTotal.setText(String.valueOf(stats.totalSmoked));
        tvAvg.setText(String.valueOf(stats.avgPerDay));

        if (stats.lastSmokedAt != null) {
            tvLastSmoked.setText("Последняя сигарета: "
                + dateTimeFormat.format(new Date(stats.lastSmokedAt)));
        } else {
            tvLastSmoked.setText("Записей пока нет");
        }
    }

    private void bindFacts(SmokingStats stats) {
        factsContainer.removeAllViews();
        List<SmokingFacts.Fact> facts = SmokingFacts.build(stats);
        boolean empty = facts.isEmpty();
        factsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        factsContainer.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        for (int i = 0; i < facts.size(); i++) {
            SmokingFacts.Fact fact = facts.get(i);
            View item = getLayoutInflater()
                .inflate(R.layout.item_smoking_fact, factsContainer, false);
            TextView title = item.findViewById(R.id.fact_title);
            TextView body = item.findViewById(R.id.fact_body);
            title.setText(fact.title());
            body.setText(fact.body());
            factsContainer.addView(item);

            if (i < facts.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp()));
                divider.setBackgroundColor(getColor(R.color.divider));
                factsContainer.addView(divider);
            }
        }
    }

    private void bindHistory(List<SmokingEntry> entries) {
        historyContainer.removeAllViews();
        boolean empty = entries == null || entries.isEmpty();
        historyEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        historyContainer.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        for (SmokingEntry entry : entries) {
            View item = getLayoutInflater()
                .inflate(R.layout.item_smoking_history, historyContainer, false);
            TextView when = item.findViewById(R.id.history_when);
            TextView note = item.findViewById(R.id.history_note);
            TextView count = item.findViewById(R.id.history_count);

            when.setText(dateTimeFormat.format(new Date(entry.smokedAt)));
            if (entry.note != null && !entry.note.isEmpty()) {
                note.setText(entry.note);
                note.setVisibility(View.VISIBLE);
            } else {
                note.setVisibility(View.GONE);
            }
            String countText = entry.count + " шт.";
            count.setText(countText);

            item.setOnLongClickListener(v -> {
                confirmDelete(entry);
                return true;
            });
            historyContainer.addView(item);
        }
    }

    private void addEntry(int count, long at) {
        DbExecutor.runVoid(() -> dao.insert(new SmokingEntry(at, count)), this::load);
    }

    private void showAddDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_smoking, null);
        EditText inputCount = view.findViewById(R.id.input_smoke_count);
        com.google.android.material.button.MaterialButton btnWhen =
            view.findViewById(R.id.btn_smoke_when);

        final long[] selectedAt = {System.currentTimeMillis()};
        btnWhen.setText(dateTimeFormat.format(new Date(selectedAt[0])));
        btnWhen.setOnClickListener(v -> pickDateTime(selectedAt, btnWhen));

        new AlertDialog.Builder(this)
            .setTitle("Добавить запись")
            .setView(view)
            .setPositiveButton("Добавить", (d, w) -> {
                int count;
                try {
                    count = Integer.parseInt(inputCount.getText().toString().trim());
                    if (count <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Введите количество сигарет", Toast.LENGTH_SHORT).show();
                    return;
                }
                addEntry(count, selectedAt[0]);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void pickDateTime(long[] selectedAt,
                              com.google.android.material.button.MaterialButton btnWhen) {
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(selectedAt[0]);

        new DatePickerDialog(this, (dateView, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.setTimeInMillis(selectedAt[0]);
            picked.set(Calendar.YEAR, year);
            picked.set(Calendar.MONTH, month);
            picked.set(Calendar.DAY_OF_MONTH, day);

            new TimePickerDialog(this, (timeView, hour, minute) -> {
                picked.set(Calendar.HOUR_OF_DAY, hour);
                picked.set(Calendar.MINUTE, minute);
                picked.set(Calendar.SECOND, 0);
                picked.set(Calendar.MILLISECOND, 0);
                selectedAt[0] = picked.getTimeInMillis();
                btnWhen.setText(dateTimeFormat.format(new Date(selectedAt[0])));
            }, picked.get(Calendar.HOUR_OF_DAY), picked.get(Calendar.MINUTE), true).show();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void confirmDelete(SmokingEntry entry) {
        new AlertDialog.Builder(this)
            .setTitle("Удалить запись?")
            .setMessage(dateTimeFormat.format(new Date(entry.smokedAt))
                + " · " + entry.count + " шт.")
            .setPositiveButton("Удалить", (d, w) ->
                DbExecutor.runVoid(() -> dao.delete(entry.id), this::load))
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
            .setTitle("Очистить историю курения?")
            .setMessage("Все записи будут удалены без возможности восстановления.")
            .setPositiveButton("Очистить", (d, w) ->
                DbExecutor.runVoid(() -> dao.deleteAll(), this::load))
            .setNegativeButton("Отмена", null)
            .show();
    }

    private String daysLabel(int days) {
        int mod100 = days % 100;
        int mod10 = days % 10;
        if (mod100 >= 11 && mod100 <= 14) return "дней без сигарет";
        if (mod10 == 1) return "день без сигарет";
        if (mod10 >= 2 && mod10 <= 4) return "дня без сигарет";
        return "дней без сигарет";
    }

    private int dp() {
        return (int) getResources().getDisplayMetrics().density;
    }

    private record LoadData(SmokingStats stats, List<SmokingEntry> entries) {
    }
}
