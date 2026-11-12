package com.example.taskmgr.ui.subs;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.SubscriptionDao;
import com.example.taskmgr.model.Subscription;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SubscriptionsActivity extends AppCompatActivity {

    private SubscriptionDao dao;
    private SubscriptionsAdapter adapter;
    private RecyclerView recycler;
    private TextView emptyView, monthTotal, nextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscriptions);

        dao = new SubscriptionDao(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Подписки");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.subs_recycler);
        emptyView = findViewById(R.id.subs_empty);
        monthTotal = findViewById(R.id.subs_month_total);
        nextView = findViewById(R.id.subs_next);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubscriptionsAdapter(this::showActions);
        recycler.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_sub);
        fab.setOnClickListener(v -> showEditDialog(null));

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        DbExecutor.run(() -> {
            List<Subscription> list = dao.getAllSorted();
            SubscriptionDao.Stats stats = dao.getStats();
            return new LoadResult(list, stats);
        }, res -> {
            if (res == null) return;
            List<Subscription> list = res.list != null ? res.list : Collections.emptyList();
            adapter.setItems(list);
            boolean empty = list.isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            recycler.setVisibility(empty ? View.GONE : View.VISIBLE);

            SubscriptionDao.Stats st = res.stats;
            monthTotal.setText(String.format(Locale.getDefault(),
                "В месяц: %.2f ₽ · В год: %.2f ₽", st.activeSum, st.activeSum * 12));
            bindNext(list);
        });
    }

    private void bindNext(List<Subscription> list) {
        for (Subscription s : list) {
            if (s.isActive) {
                String date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    .format(new Date(s.nextChargeAt));
                nextView.setText(String.format(Locale.getDefault(),
                    "Следующее: %s — %s — %.2f ₽", s.name, date, s.amount));
                return;
            }
        }
        nextView.setText("Следующее списание: —");
    }

    private void showActions(Subscription s) {
        new AlertDialog.Builder(this)
            .setTitle(s.name)
            .setItems(new String[]{"Оплачено (сдвинуть на месяц)", "Редактировать", "Удалить"}, (d, which) -> {
                if (which == 0) {
                    DbExecutor.runVoid(() -> dao.markPaid(s.id), this::load);
                } else if (which == 1) {
                    showEditDialog(s);
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("Удалить подписку «" + s.name + "»?")
                        .setPositiveButton("Удалить", (dd, ww) ->
                            DbExecutor.runVoid(() -> dao.delete(s.id), this::load))
                        .setNegativeButton("Отмена", null)
                        .show();
                }
            })
            .show();
    }

    private void showEditDialog(Subscription existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_subscription, null);
        android.widget.EditText inputName = view.findViewById(R.id.input_sub_name);
        android.widget.EditText inputAmount = view.findViewById(R.id.input_sub_amount);
        android.widget.EditText inputDay = view.findViewById(R.id.input_sub_day);
        android.widget.EditText inputDesc = view.findViewById(R.id.input_sub_description);
        com.google.android.material.materialswitch.MaterialSwitch swActive =
            view.findViewById(R.id.switch_sub_active);

        if (existing != null) {
            inputName.setText(existing.name);
            inputAmount.setText(String.valueOf(existing.amount));
            inputDay.setText(String.valueOf(existing.chargeDay));
            if (existing.description != null) inputDesc.setText(existing.description);
            swActive.setChecked(existing.isActive);
        } else {
            swActive.setChecked(true);
        }

        new AlertDialog.Builder(this)
            .setTitle(existing == null ? "Новая подписка" : "Редактировать подписку")
            .setView(view)
            .setPositiveButton("Сохранить", (d, w) -> {
                String name = inputName.getText().toString().trim();
                if (name.isEmpty()) {
                    android.widget.Toast.makeText(this, "Введите название",
                        android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                double amount;
                try {
                    amount = Double.parseDouble(inputAmount.getText().toString().trim());
                    if (amount < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    android.widget.Toast.makeText(this, "Некорректная сумма",
                        android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                int day;
                try {
                    day = Integer.parseInt(inputDay.getText().toString().trim());
                    if (day < 1 || day > 31) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    android.widget.Toast.makeText(this, "День должен быть 1–31",
                        android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                String desc = inputDesc.getText().toString().trim();
                boolean active = swActive.isChecked();

                if (existing == null) {
                    long nextAt = SubscriptionDao.computeNextChargeAt(day);
                    Subscription s = new Subscription(name, amount, day, nextAt,
                        desc.isEmpty() ? null : desc, active);
                    DbExecutor.runVoid(() -> dao.insert(s), this::load);
                } else {
                    existing.name = name;
                    existing.amount = amount;
                    if (existing.chargeDay != day) {
                        existing.chargeDay = day;
                        existing.nextChargeAt = SubscriptionDao.computeNextChargeAt(day);
                    }
                    existing.description = desc.isEmpty() ? null : desc;
                    existing.isActive = active;
                    DbExecutor.runVoid(() -> dao.update(existing), this::load);
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private record LoadResult(List<Subscription> list, SubscriptionDao.Stats stats) {
    }
}
