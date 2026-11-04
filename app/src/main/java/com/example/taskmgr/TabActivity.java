package com.example.taskmgr;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class TabActivity extends AppCompatActivity {

    public static final String EXTRA_TAB_ID = "tab_id";
    public static final String EXTRA_TAB_NAME = "tab_name";
    public static final String EXTRA_IS_AUTO = "is_auto";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_section); // временно используем ту же разметку

        String name = getIntent().getStringExtra(EXTRA_TAB_NAME);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(name != null ? name : "Вкладка");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}