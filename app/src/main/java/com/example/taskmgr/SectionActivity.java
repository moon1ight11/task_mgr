package com.example.taskmgr;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.dao.SectionDao;
import com.example.taskmgr.dao.TabDao;
import com.example.taskmgr.model.Section;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.utill.AppLock;
import com.example.taskmgr.utill.DbExecutor;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class SectionActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION_ID = "section_id";
    public static final String EXTRA_SECTION_NAME = "section_name";

    private long sectionId;
    private String sectionName;
    private boolean isAutoSection;

    private TabDao tabDao;
    private SectionDao sectionDao;
    private TabAdapter adapter;
    private RecyclerView recycler;
    private TextView emptyView;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_section);

        sectionId = getIntent().getLongExtra(EXTRA_SECTION_ID, -1);
        sectionName = getIntent().getStringExtra(EXTRA_SECTION_NAME);
        isAutoSection = "Авто".equals(sectionName);

        // Проверка приватности: если раздел приватный и сессия не разблокирована — закрываем.
        sectionDao = new SectionDao(this);
        Section s = sectionDao.getById(sectionId);
        if (s == null) {
            finish();
            return;
        }
        if (s.isPrivate && !AppLock.isPrivateUnlocked()) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(sectionName != null ? sectionName : "Раздел");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tabDao = new TabDao(this);

        recycler = findViewById(R.id.tabs_recycler);
        emptyView = findViewById(R.id.empty_view);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TabAdapter(
                isAutoSection,
                tab -> {
                    Intent i = new Intent(SectionActivity.this, TabActivity.class);
                    i.putExtra(TabActivity.EXTRA_TAB_ID, tab.id);
                    i.putExtra(TabActivity.EXTRA_TAB_NAME, tab.name);
                    i.putExtra(TabActivity.EXTRA_IS_AUTO, isAutoSection);
                    startActivity(i);
                },
                this::showTabActionsDialog
        );
        recycler.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_tab);
        fab.setOnClickListener(v -> showAddTabDialog());

        loadTabs();
    }

    private void loadTabs() {
        if (loading) return;
        loading = true;
        DbExecutor.run(() -> tabDao.getBySection(sectionId), tabs -> {
            loading = false;
            if (tabs == null) tabs = java.util.Collections.emptyList();
            adapter.setItems(tabs);
            boolean empty = tabs.isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    // ============ Диалог действий по вкладке ============

    private void showTabActionsDialog(Tab tab) {
        new AlertDialog.Builder(this)
                .setTitle(tab.name)
                .setItems(new String[]{"Редактировать", "Удалить"}, (d, which) -> {
                    if (which == 0) showEditTabDialog(tab);
                    else confirmDeleteTab(tab);
                })
                .show();
    }

    private void showEditTabDialog(Tab tab) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_tab, null);
        EditText inputName = view.findViewById(R.id.input_name);
        EditText inputYear = view.findViewById(R.id.input_year);
        EditText inputKm = view.findViewById(R.id.input_km);

        inputName.setText(tab.name);

        if (isAutoSection) {
            inputYear.setVisibility(View.VISIBLE);
            inputKm.setVisibility(View.VISIBLE);
            if (tab.year != null) inputYear.setText(String.valueOf(tab.year));
            if (tab.currentKm != null) inputKm.setText(String.valueOf(tab.currentKm));
        }

        new AlertDialog.Builder(this)
                .setTitle("Редактировать вкладку")
                .setView(view)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String name = inputName.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    tab.name = name;
                    if (isAutoSection) {
                        Integer year = parseIntOrNull(inputYear.getText().toString());
                        Integer km = parseIntOrNull(inputKm.getText().toString());
                        tab.year = year;
                        tab.currentKm = km;
                    }
                    DbExecutor.runVoid(() -> tabDao.update(tab), this::loadTabs);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmDeleteTab(Tab tab) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить вкладку «" + tab.name + "»?")
                .setMessage("Будут удалены все задачи, история, покупки и справочник этой вкладки. Это действие нельзя отменить.")
                .setPositiveButton("Удалить", (d, w) -> {
                    DbExecutor.runVoid(() -> tabDao.delete(tab.id), this::loadTabs);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ============ Диалог добавления вкладки ============

    private void showAddTabDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_tab, null);
        EditText inputName = view.findViewById(R.id.input_name);
        EditText inputYear = view.findViewById(R.id.input_year);
        EditText inputKm = view.findViewById(R.id.input_km);

        if (isAutoSection) {
            inputYear.setVisibility(View.VISIBLE);
            inputKm.setVisibility(View.VISIBLE);
        }

        new AlertDialog.Builder(this)
                .setTitle("Новая вкладка")
                .setView(view)
                .setPositiveButton("Добавить", (d, w) -> {
                    String name = inputName.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Tab t = new Tab(sectionId, name);
                    if (isAutoSection) {
                        t.year = parseIntOrNull(inputYear.getText().toString());
                        t.currentKm = parseIntOrNull(inputKm.getText().toString());
                    }
                    DbExecutor.runVoid(() -> tabDao.insert(t), this::loadTabs);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ============ Хелпер ============

    private Integer parseIntOrNull(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}