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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class SectionActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION_ID = "section_id";
    public static final String EXTRA_SECTION_NAME = "section_name";

    private long sectionId;
    private String sectionName;
    private boolean isAutoSection;

    private TabDao tabDao;
    private TabAdapter adapter;
    private RecyclerView recycler;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_section);

        sectionId = getIntent().getLongExtra(EXTRA_SECTION_ID, -1);
        sectionName = getIntent().getStringExtra(EXTRA_SECTION_NAME);
        isAutoSection = "Авто".equals(sectionName);

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
        adapter = new TabAdapter(isAutoSection, tab -> {
            Intent i = new Intent(SectionActivity.this, TabActivity.class);
            i.putExtra(TabActivity.EXTRA_TAB_ID, tab.id);
            i.putExtra(TabActivity.EXTRA_TAB_NAME, tab.name);
            i.putExtra(TabActivity.EXTRA_IS_AUTO, isAutoSection);
            startActivity(i);
        });
        recycler.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_tab);
        fab.setOnClickListener(v -> showAddTabDialog());

        loadTabs();
    }

    private void loadTabs() {
        List<Tab> tabs = tabDao.getBySection(sectionId);
        adapter.setItems(tabs);
        boolean empty = tabs.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

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
                        String yearStr = inputYear.getText().toString().trim();
                        String kmStr = inputKm.getText().toString().trim();
                        if (!yearStr.isEmpty()) {
                            try { t.year = Integer.parseInt(yearStr); } catch (NumberFormatException ignored) {}
                        }
                        if (!kmStr.isEmpty()) {
                            try { t.currentKm = Integer.parseInt(kmStr); } catch (NumberFormatException ignored) {}
                        }
                    }
                    tabDao.insert(t);
                    loadTabs();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}