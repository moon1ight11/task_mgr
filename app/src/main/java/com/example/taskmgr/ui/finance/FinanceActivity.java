package com.example.taskmgr.ui.finance;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.taskmgr.R;
import com.google.android.material.tabs.TabLayout;

public class FinanceActivity extends AppCompatActivity {

    private View periodsView, settingsView, statsView;
    private FinancePeriodsController periodsController;
    private FinanceSettingsController settingsController;
    private FinanceStatsController statsController;
    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Финансы");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        FrameLayout content = findViewById(R.id.finance_content);

        // Инфлейтим три вкладки
        periodsView = getLayoutInflater().inflate(R.layout.fragment_finance_periods, content, false);
        settingsView = getLayoutInflater().inflate(R.layout.fragment_finance_settings, content, false);
        statsView = getLayoutInflater().inflate(R.layout.fragment_finance_stats, content, false);

        content.addView(periodsView);
        content.addView(settingsView);
        content.addView(statsView);

        // Контроллеры
        periodsController = new FinancePeriodsController(this, periodsView);
        settingsController = new FinanceSettingsController(this, settingsView);
        statsController = new FinanceStatsController(this, statsView);

        TabLayout tabs = findViewById(R.id.finance_tabs);
        tabs.addTab(tabs.newTab().setText("Периоды"));
        tabs.addTab(tabs.newTab().setText("Настройки"));
        tabs.addTab(tabs.newTab().setText("Статистика"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateVisibleTab();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        findViewById(R.id.fab_finance_add).setOnClickListener(v -> {
            if (currentTab == 0) periodsController.showAddExpenseDialog();
            else if (currentTab == 1) settingsController.showAddIncomeDialog();
            // currentTab == 2 — статистика, FAB скрыт
        });

        updateVisibleTab();
    }

    private void updateVisibleTab() {
        periodsView.setVisibility(currentTab == 0 ? View.VISIBLE : View.GONE);
        settingsView.setVisibility(currentTab == 1 ? View.VISIBLE : View.GONE);
        statsView.setVisibility(currentTab == 2 ? View.VISIBLE : View.GONE);

        // FAB скрыт на вкладке «Статистика»
        findViewById(R.id.fab_finance_add).setVisibility(
            currentTab == 2 ? View.GONE : View.VISIBLE);

        if (currentTab == 0) periodsController.load();
        else if (currentTab == 1) settingsController.load();
        else statsController.load();
    }
}
