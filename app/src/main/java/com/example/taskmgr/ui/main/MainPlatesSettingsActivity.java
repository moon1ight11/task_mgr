package com.example.taskmgr.ui.main;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.MainPlatesDao;
import com.example.taskmgr.model.MainPlate;
import com.example.taskmgr.util.DbExecutor;

import java.util.ArrayList;
import java.util.List;

public class MainPlatesSettingsActivity extends AppCompatActivity {

    private MainPlatesDao dao;
    private MainPlatesSettingsAdapter adapter;
    private List<MainPlate> plates = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_plates_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Плитки главного экрана");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        dao = new MainPlatesDao(this);

        RecyclerView recycler = findViewById(R.id.plates_settings_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MainPlatesSettingsAdapter(
            this::onVisibleChanged,
            this::onMoveUp,
            this::onMoveDown
        );
        recycler.setAdapter(adapter);

        load();
    }

    private void load() {
        DbExecutor.run(() -> dao.getAll(), list -> {
            if (list == null) return;
            plates = list;
            adapter.setItems(plates);
        });
    }

    private void onVisibleChanged(MainPlate p, boolean visible) {
        DbExecutor.runVoid(() -> dao.setVisible(p.key, visible), this::load);
    }

    private void onMoveUp(int position) {
        if (position <= 0 || position >= plates.size()) return;
        MainPlate a = plates.get(position - 1);
        MainPlate b = plates.get(position);
        DbExecutor.runVoid(() -> dao.swap(a.key, b.key), this::load);
    }

    private void onMoveDown(int position) {
        if (position < 0 || position >= plates.size() - 1) return;
        MainPlate a = plates.get(position);
        MainPlate b = plates.get(position + 1);
        DbExecutor.runVoid(() -> dao.swap(a.key, b.key), this::load);
    }
}
