package com.example.taskmgr;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import android.content.Intent;
import com.example.taskmgr.dao.SectionDao;
import com.example.taskmgr.model.Section;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.dao.SectionDao;
import com.example.taskmgr.dao.TabDao;
import com.example.taskmgr.dao.TaskDao;
import com.example.taskmgr.model.Section;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private TabLayout mainTabs;
    private RecyclerView tasksRecycler;
    private android.widget.TextView emptyView;
    private TaskAdapter taskAdapter;

    private TaskDao taskDao;

    // текущая выбранная вкладка главной: 0 = Дедлайн, 1 = Важные
    private int currentMainTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        taskDao = new TaskDao(this);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("TaskMGR");
        }

        // Drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                android.R.string.ok, android.R.string.cancel);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navView.setNavigationItemSelectedListener(this::onNavItemSelected);

        // Tabs
        mainTabs = findViewById(R.id.main_tabs);
        mainTabs.addTab(mainTabs.newTab().setText("Дедлайн"));
        mainTabs.addTab(mainTabs.newTab().setText("Важные"));
        mainTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentMainTab = tab.getPosition();
                loadTasks();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Recycler
        tasksRecycler = findViewById(R.id.tasks_recycler);
        emptyView = findViewById(R.id.empty_view);
        tasksRecycler.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TaskAdapter(task -> {
            Toast.makeText(this, "Задача: " + task.title, Toast.LENGTH_SHORT).show();
        });
        tasksRecycler.setAdapter(taskAdapter);

        // Первичное заполнение разделов (если база пуста)
        seedSectionsIfEmpty();

        // Загружаем задачи
        loadTasks();
    }

    private void seedSectionsIfEmpty() {
        SectionDao sectionDao = new SectionDao(this);
        if (!sectionDao.getAll().isEmpty()) return;

        long home = sectionDao.insert(new Section("Дом", "home", 1));
        long garage = sectionDao.insert(new Section("Гараж", "garage", 2));
        long auto = sectionDao.insert(new Section("Авто", "car", 3));
        long it = sectionDao.insert(new Section("IT", "it", 4));
        long health = sectionDao.insert(new Section("Здоровье", "health", 5));
        long ent = sectionDao.insert(new Section("Развлечения", "ent", 6));

        TabDao tabDao = new TabDao(this);
        tabDao.insert(new Tab(home, "Тим"));
        tabDao.insert(new Tab(home, "ЮВ"));
        tabDao.insert(new Tab(auto, "Королла"));
        tabDao.insert(new Tab(it, "Работа"));
        tabDao.insert(new Tab(ent, "Книги"));
    }

    private void loadTasks() {
        List<Task> tasks;
        if (currentMainTab == 0) {
            tasks = taskDao.getActiveSortedByDue();
        } else {
            tasks = taskDao.getActiveImportant();
        }
        taskAdapter.setItems(tasks);
        boolean empty = tasks.isEmpty();
        emptyView.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        tasksRecycler.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        drawerLayout.closeDrawer(GravityCompat.START);
        int id = item.getItemId();
        String sectionName = null;
        if (id == R.id.nav_home) sectionName = "Дом";
        else if (id == R.id.nav_garage) sectionName = "Гараж";
        else if (id == R.id.nav_auto) sectionName = "Авто";
        else if (id == R.id.nav_it) sectionName = "IT";
        else if (id == R.id.nav_health) sectionName = "Здоровье";
        else if (id == R.id.nav_ent) sectionName = "Развлечения";

        if (sectionName != null) {
            SectionDao sectionDao = new SectionDao(this);
            List<Section> sections = sectionDao.getAll();
            for (Section s : sections) {
                if (s.name.equals(sectionName)) {
                    Intent i = new Intent(this, SectionActivity.class);
                    i.putExtra(SectionActivity.EXTRA_SECTION_ID, s.id);
                    i.putExtra(SectionActivity.EXTRA_SECTION_NAME, s.name);
                    startActivity(i);
                    return true;
                }
            }
            Toast.makeText(this, "Раздел не найден: " + sectionName, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}