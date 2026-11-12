package com.example.taskmgr.ui.utilities;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.taskmgr.R;
import com.google.android.material.tabs.TabLayout;

public class UtilitiesActivity extends AppCompatActivity {

    private View billView, statsView;
    private UtilitiesBillController billController;
    private UtilitiesStatsController statsController;
    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_utilities);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("ЖКХ");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        FrameLayout content = findViewById(R.id.utilities_content);

        billView = getLayoutInflater().inflate(R.layout.fragment_utilities_bill, content, false);
        statsView = getLayoutInflater().inflate(R.layout.fragment_utilities_stats, content, false);
        content.addView(billView);
        content.addView(statsView);

        billController = new UtilitiesBillController(this, billView);
        statsController = new UtilitiesStatsController(this, statsView);

        TabLayout tabs = findViewById(R.id.utilities_tabs);
        tabs.addTab(tabs.newTab().setText("Квитанция"));
        tabs.addTab(tabs.newTab().setText("Статистика"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateVisibleTab();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        findViewById(R.id.fab_add_meter).setOnClickListener(v -> {
            if (currentTab == 0) billController.showAddMeterDialog();
        });

        updateVisibleTab();
    }

    private void updateVisibleTab() {
        billView.setVisibility(currentTab == 0 ? View.VISIBLE : View.GONE);
        statsView.setVisibility(currentTab == 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.fab_add_meter).setVisibility(
            currentTab == 0 ? View.VISIBLE : View.GONE);
        if (currentTab == 0) billController.load();
        else statsController.load();
    }
}
