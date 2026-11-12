// com/example/taskmgr/ui/utilities/UtilitiesBillController.java
package com.example.taskmgr.ui.utilities;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.UtilityMeterDao;
import com.example.taskmgr.data.dao.UtilityPeriodDao;
import com.example.taskmgr.data.dao.UtilityReadingDao;
import com.example.taskmgr.model.UtilityMeter;
import com.example.taskmgr.model.UtilityPeriod;
import com.example.taskmgr.model.UtilityReading;
import com.example.taskmgr.util.DbExecutor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UtilitiesBillController {

    private final Activity activity;
    private final View root;
    private final UtilityMeterDao meterDao;
    private final UtilityPeriodDao periodDao;
    private final UtilityReadingDao readingDao;
    private final UtilitiesReadingAdapter adapter;

    private int year, month; // Calendar.MONTH
    private String selectedHouse;
    private UtilityPeriod currentPeriod;
    private List<UtilityMeter> activeMeters = new ArrayList<>();
    private Map<Long, UtilityReading> readingsByMeter = new HashMap<>();

    public UtilitiesBillController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        this.meterDao = new UtilityMeterDao(activity);
        this.periodDao = new UtilityPeriodDao(activity);
        this.readingDao = new UtilityReadingDao(activity);

        Calendar c = Calendar.getInstance();
        this.year = c.get(Calendar.YEAR);
        this.month = c.get(Calendar.MONTH);

        RecyclerView recycler = root.findViewById(R.id.readings_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new UtilitiesReadingAdapter(activity, this::onReadingClick);
        recycler.setAdapter(adapter);

        root.findViewById(R.id.btn_prev_month).setOnClickListener(v -> { month--; if (month < 0) { month = 11; year--; } load(); });
        root.findViewById(R.id.btn_next_month).setOnClickListener(v -> { month++; if (month > 11) { month = 0; year++; } load(); });
        root.findViewById(R.id.row_house).setOnClickListener(v -> showHousePicker());
        root.findViewById(R.id.btn_create_period).setOnClickListener(v -> createPeriod());
        root.findViewById(R.id.btn_toggle_paid).setOnClickListener(v -> togglePaid());

        load();
    }

    public void load() {
        final int y = year, m = month;
        final String house = selectedHouse;
        DbExecutor.run(() -> {
            List<UtilityMeter> meters = meterDao.getAll(true);
            String houseToUse = house;
            if (houseToUse == null) houseToUse = periodDao.getLastHouse();
            UtilityPeriod p = houseToUse != null ? periodDao.find(y, m, houseToUse) : null;
            List<UtilityReading> readings = p != null ? readingDao.getByPeriod(p.id) : new ArrayList<>();
            List<String> houses = periodDao.getHouses();
            return new LoadResult(meters, p, readings, houseToUse, houses);
        }, res -> bind(res));
    }

    private void bind(LoadResult res) {
        if (res == null) return;
        selectedHouse = res.house;
        activeMeters = res.meters;
        currentPeriod = res.period;
        readingsByMeter.clear();
        for (UtilityReading r : res.readings) readingsByMeter.put(r.meterId, r);

        // Заголовок месяца
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        SimpleDateFormat fmt = new SimpleDateFormat("LLLL yyyy", new Locale("ru"));
        String monthLabel = fmt.format(c.getTime());
        ((TextView) root.findViewById(R.id.tv_month)).setText(capitalize(monthLabel));

        // Дом
        TextView tvHouse = root.findViewById(R.id.tv_house);
        tvHouse.setText(selectedHouse != null ? selectedHouse : "—");

        // Сводка
        TextView tvTitle = root.findViewById(R.id.tv_summary_title);
        TextView tvTotal = root.findViewById(R.id.tv_summary_total);
        TextView tvNote = root.findViewById(R.id.tv_summary_note);
        com.google.android.material.button.MaterialButton btnPaid = root.findViewById(R.id.btn_toggle_paid);
        View emptyHint = root.findViewById(R.id.tv_empty_hint);
        View btnCreate = root.findViewById(R.id.btn_create_period);
        RecyclerView recycler = root.findViewById(R.id.readings_recycler);
        TextView footer = root.findViewById(R.id.tv_footer_total);

        tvTitle.setText(capitalize(monthLabel) + (selectedHouse != null ? " · " + selectedHouse : ""));

        if (currentPeriod == null) {
            tvTotal.setText("—");
            tvNote.setVisibility(View.GONE);
            btnPaid.setVisibility(View.GONE);
            emptyHint.setVisibility(View.VISIBLE);
            btnCreate.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            footer.setVisibility(View.GONE);
            adapter.setItems(new ArrayList<>(), new HashMap<>());
            return;
        }

        emptyHint.setVisibility(View.GONE);
        btnCreate.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        footer.setVisibility(View.VISIBLE);

        double total = 0;
        for (UtilityReading r : res.readings) total += r.amount;
        tvTotal.setText(String.format(Locale.getDefault(), "%.2f ₽", total));

        if (currentPeriod.note != null && !currentPeriod.note.isEmpty()) {
            tvNote.setText(currentPeriod.note);
            tvNote.setVisibility(View.VISIBLE);
        } else tvNote.setVisibility(View.GONE);

        btnPaid.setVisibility(View.VISIBLE);
        btnPaid.setText(currentPeriod.paid ? "Вернуть в неоплачено" : "Оплатить всё");
        btnPaid.setTag(currentPeriod.paid);

        footer.setText("Итого за месяц: " + String.format(Locale.getDefault(), "%.2f ₽", total));

        adapter.setItems(activeMeters, readingsByMeter);
    }

    private void onReadingClick(UtilityMeter meter) {
        if (currentPeriod == null) {
            Toast.makeText(activity, "Сначала создайте квитанцию за месяц", Toast.LENGTH_SHORT).show();
            return;
        }
        UtilityReading existing = readingsByMeter.get(meter.id);
        showReadingDialog(meter, existing);
    }

    private void showReadingDialog(UtilityMeter meter, UtilityReading existing) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_utility_reading, null);
        EditText inPrev = v.findViewById(R.id.input_previous);
        EditText inCurr = v.findViewById(R.id.input_current);
        EditText inRate = v.findViewById(R.id.input_rate);
        TextView tvCalc = v.findViewById(R.id.tv_calc);

        if (existing != null) {
            inPrev.setText(formatNum(existing.previousValue));
            inCurr.setText(formatNum(existing.currentValue));
            inRate.setText(formatNum(existing.rateApplied));
        } else {
            // Предзаполняем предыдущее = последнее current для этого счётчика
            double last = readingDao.getLastCurrentValue(meter.id);
            if (last > 0) {
                inPrev.setText(formatNum(last));
            } else {
                inPrev.setText("");
                inPrev.setHint("0");
            }
            inCurr.setText("");
            inCurr.setHint(formatNum(last));
            inRate.setText(formatNum(meter.rate));
        }

        Runnable recalc = () -> {
            double prev = parseDouble(inPrev.getText().toString());
            double curr = parseDouble(inCurr.getText().toString());
            double rate = parseDouble(inRate.getText().toString());
            double consumption = Math.max(0, curr - prev);
            double amount = consumption * rate;
            tvCalc.setText(String.format(Locale.getDefault(),
                "Расход: %s %s — Итого: %.2f ₽",
                formatNum(consumption), meter.unit, amount));
        };
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { recalc.run(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        };
        inPrev.addTextChangedListener(tw);
        inCurr.addTextChangedListener(tw);
        inRate.addTextChangedListener(tw);
        recalc.run();

        new AlertDialog.Builder(activity)
            .setTitle(meter.name + " — " + meter.unit)
            .setView(v)
            .setPositiveButton("Сохранить", (d, w) -> {
                double prev = parseDouble(inPrev.getText().toString());
                double curr = parseDouble(inCurr.getText().toString());
                double rate = parseDouble(inRate.getText().toString());
                if (curr < prev) {
                    Toast.makeText(activity, "Текущее не может быть меньше предыдущего",
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                double consumption = curr - prev;
                double amount = consumption * rate;

                UtilityReading r = new UtilityReading();
                r.id = existing != null ? existing.id : 0;
                r.periodId = currentPeriod.id;
                r.meterId = meter.id;
                r.previousValue = prev;
                r.currentValue = curr;
                r.consumption = consumption;
                r.rateApplied = rate;
                r.amount = amount;
                r.createdAt = System.currentTimeMillis();

                DbExecutor.runVoid(() -> readingDao.insert(r), this::load);
            })
            .setNegativeButton("Отмена", null)
            .setNeutralButton(existing != null ? "Удалить" : null, (d, w) -> {
                if (existing != null) {
                    DbExecutor.runVoid(() -> readingDao.delete(existing.id), this::load);
                }
            })
            .show();
    }

    private void showHousePicker() {
        DbExecutor.run(() -> {
            List<String> houses = periodDao.getHouses();
            String last = periodDao.getLastHouse();
            return new HouseData(houses, last);
        }, data -> {
            if (data == null) return;
            List<String> items = new ArrayList<>(data.houses);
            items.add("+ Новый дом");
            new AlertDialog.Builder(activity)
                .setTitle("Выберите дом")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == data.houses.size()) {
                        promptNewHouse();
                    } else {
                        selectedHouse = data.houses.get(which);
                        load();
                    }
                })
                .show();
        });
    }

    private void promptNewHouse() {
        EditText input = new EditText(activity);
        input.setHint("Название / адрес");
        new AlertDialog.Builder(activity)
            .setTitle("Новый дом")
            .setView(input)
            .setPositiveButton("OK", (d, w) -> {
                String h = input.getText().toString().trim();
                if (!h.isEmpty()) {
                    selectedHouse = h;
                    load();
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void createPeriod() {
        String house = selectedHouse;
        if (house == null) house = periodDao.getLastHouse();
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_utility_period, null);
        EditText inHouse = v.findViewById(R.id.input_house);
        EditText inNote = v.findViewById(R.id.input_note);
        if (house != null) inHouse.setText(house);

        final String[] houseFinal = {house};
        AlertDialog dlg = new AlertDialog.Builder(activity)
            .setTitle("Квитанция за " + monthLabel())
            .setView(v)
            .setPositiveButton("Создать", null)
            .setNegativeButton("Отмена", null)
            .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(bv -> {
            String h = inHouse.getText().toString().trim();
            if (h.isEmpty()) {
                Toast.makeText(activity, "Введите дом/адрес", Toast.LENGTH_SHORT).show();
                return;
            }
            String note = inNote.getText().toString().trim();
            UtilityPeriod p = new UtilityPeriod(year, month, h,
                note.isEmpty() ? null : note);
            DbExecutor.runVoid(() -> periodDao.insert(p), () -> {
                selectedHouse = h;
                dlg.dismiss();
                load();
            });
        }));
        dlg.show();
    }

    private void togglePaid() {
        if (currentPeriod == null) return;
        final boolean newPaid = !currentPeriod.paid;
        DbExecutor.runVoid(() -> periodDao.setPaid(currentPeriod.id, newPaid), this::load);
    }

    private String monthLabel() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        return capitalize(new SimpleDateFormat("LLLL yyyy", new Locale("ru")).format(c.getTime()));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try { return Double.parseDouble(s.trim().replace(',', '.')); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String formatNum(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.format(Locale.getDefault(), "%.2f", v);
    }

    private static class LoadResult {
        final List<UtilityMeter> meters;
        final UtilityPeriod period;
        final List<UtilityReading> readings;
        final String house;
        final List<String> houses;
        LoadResult(List<UtilityMeter> m, UtilityPeriod p, List<UtilityReading> r,
                   String h, List<String> hs) {
            meters = m; period = p; readings = r; house = h; houses = hs;
        }
    }

    public void showAddMeterDialog() {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_add_meter, null);
        EditText inName = v.findViewById(R.id.input_meter_name);
        EditText inUnit = v.findViewById(R.id.input_meter_unit);
        EditText inRate = v.findViewById(R.id.input_meter_rate);

        new AlertDialog.Builder(activity)
            .setTitle("Новый счётчик")
            .setView(v)
            .setPositiveButton("Добавить", (d, w) -> {
                String name = inName.getText().toString().trim();
                String unit = inUnit.getText().toString().trim();
                double rate;
                try {
                    rate = Double.parseDouble(inRate.getText().toString().trim().replace(',', '.'));
                    if (rate < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    Toast.makeText(activity, "Некорректный тариф", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (name.isEmpty() || unit.isEmpty()) {
                    Toast.makeText(activity, "Введите название и единицу", Toast.LENGTH_SHORT).show();
                    return;
                }
                UtilityMeter m = new UtilityMeter(name, unit, rate);
                DbExecutor.runVoid(() -> meterDao.insert(m), this::load);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private record HouseData(List<String> houses, String last) {}
}
