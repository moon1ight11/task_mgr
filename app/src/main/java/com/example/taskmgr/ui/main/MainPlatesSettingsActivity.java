package com.example.taskmgr.ui.main;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
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
    private ItemTouchHelper itemTouchHelper;

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

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public boolean isLongPressDragEnabled() {
                return false; // драг только за ручку
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                int drag = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                return makeMovementFlags(drag, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;

                adapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // не используется
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // Когда пользователь отпустил палец — сохраняем порядок в БД.
                saveOrder();
            }
        });
        itemTouchHelper.attachToRecyclerView(recycler);

        adapter = new MainPlatesSettingsAdapter(
            this::onVisibleChanged,
            holder -> itemTouchHelper.startDrag(holder)
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

    /**
     * Сохраняет текущий порядок из адаптера в БД.
     * sort_order пересчитывается как индекс в списке: 0, 1, 2, ...
     */
    private void saveOrder() {
        List<MainPlate> ordered = new ArrayList<>(adapter.getItems());
        DbExecutor.runVoid(() -> dao.reorder(ordered), () -> {
            // после сохранения не перезагружаем — порядок уже правильный
        });
    }
}
