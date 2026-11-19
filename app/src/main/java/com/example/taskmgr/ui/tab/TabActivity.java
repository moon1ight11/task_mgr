package com.example.taskmgr.ui.tab;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.HistoryDao;
import com.example.taskmgr.data.dao.ReferenceDao;
import com.example.taskmgr.data.dao.SectionDao;
import com.example.taskmgr.data.dao.ShoppingDao;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.data.dao.TaskDao;
import com.example.taskmgr.model.Section;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.util.AppLock;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class TabActivity extends AppCompatActivity {

    public static final String EXTRA_TAB_ID = "tab_id";
    public static final String EXTRA_TAB_NAME = "tab_name";
    public static final String EXTRA_IS_AUTO = "is_auto";
    public static final String EXTRA_TASK_ID = "task_id";
    private static final String STATE_SECTION_INDEX = "state_section_index";

    private final List<Integer> tabIndexToSection = new ArrayList<>();
    private TabDao tabDao;
    private TabLayout tabTabs;
    private FloatingActionButton fabAdd;
    private View viewTasks;
    private View viewShopping;
    private View viewReference;
    private Tab currentTab;
    private int currentSectionIndex = 0;

    private TasksSectionController tasksController;
    private ShoppingSectionController shoppingController;
    private ReferenceSectionController referenceController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab);

        String tabName = getIntent().getStringExtra(EXTRA_TAB_NAME);
        long extraTaskId = getIntent().getLongExtra(EXTRA_TASK_ID, -1);

        tabDao = new TabDao(this);
        Tab tab = tabDao.getById(getIntent().getLongExtra(EXTRA_TAB_ID, -1));
        if (tab == null) {
            finish();
            return;
        }
        currentTab = tab;

        SectionDao sectionDao = new SectionDao(this);
        Section section = sectionDao.getById(tab.sectionId);
        if (section != null && section.isPrivate && !AppLock.isPrivateUnlocked()) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(tabName != null ? tabName : "Вкладка");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tabTabs = findViewById(R.id.tab_tabs);
        FrameLayout contentContainer = findViewById(R.id.content_container);
        fabAdd = findViewById(R.id.fab_add);

        viewTasks = getLayoutInflater().inflate(R.layout.fragment_tasks, contentContainer, false);
        viewShopping = getLayoutInflater().inflate(R.layout.fragment_shopping, contentContainer, false);
        viewReference = getLayoutInflater().inflate(R.layout.fragment_reference, contentContainer, false);
        contentContainer.addView(viewTasks);
        contentContainer.addView(viewShopping);
        contentContainer.addView(viewReference);

        tasksController = new TasksSectionController(this, viewTasks, getIntent().getLongExtra(EXTRA_TAB_ID, -1), getIntent().getBooleanExtra(EXTRA_IS_AUTO, false),
            new TaskDao(this), tabDao, new HistoryDao(this), extraTaskId,
            this::invalidateOptionsMenu);
        shoppingController = new ShoppingSectionController(this, viewShopping, getIntent().getLongExtra(EXTRA_TAB_ID, -1),
            new ShoppingDao(this));
        referenceController = new ReferenceSectionController(this, viewReference, getIntent().getLongExtra(EXTRA_TAB_ID, -1),
            new ReferenceDao(this));

        rebuildTabLayout();

        if (savedInstanceState != null) {
            int savedSectionIndex = savedInstanceState.getInt(STATE_SECTION_INDEX, 0);
            int tabPos = -1;
            for (int i = 0; i < tabIndexToSection.size(); i++) {
                if (tabIndexToSection.get(i) == savedSectionIndex) {
                    tabPos = i;
                    break;
                }
            }
            if (tabPos >= 0) {
                if (tabPos != tabTabs.getSelectedTabPosition()) {
                    TabLayout.Tab selected = tabTabs.getTabAt(tabPos);
                    if (selected != null) selected.select();
                } else {
                    showTab(savedSectionIndex);
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SECTION_INDEX, currentSectionIndex);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tab, menu);
        android.view.MenuItem historyItem = menu.findItem(R.id.action_toggle_history);
        if (historyItem != null) {
            boolean visible = (currentSectionIndex == 0);
            historyItem.setVisible(visible);
            if (visible) {
                boolean showingHistory = tasksController != null
                    && tasksController.isShowingHistory();
                historyItem.setTitle(showingHistory ? "Задачи" : "История");
                historyItem.setIcon(showingHistory
                    ? android.R.drawable.ic_menu_agenda
                    : android.R.drawable.ic_menu_recent_history);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_tab_settings) {
            showTabSettingsDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_toggle_history) {
            if (currentSectionIndex == 0 && tasksController != null) {
                tasksController.setHistoryMode(!tasksController.isShowingHistory());
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void rebuildTabLayout() {
        tabTabs.clearOnTabSelectedListeners();
        tabTabs.removeAllTabs();
        tabIndexToSection.clear();

        if (currentTab.showTasks) {
            tabTabs.addTab(tabTabs.newTab().setText("Задачи"));
            tabIndexToSection.add(0);
        }
        if (currentTab.showShopping) {
            tabTabs.addTab(tabTabs.newTab().setText("Покупки"));
            tabIndexToSection.add(1);
        }
        if (currentTab.showReference) {
            tabTabs.addTab(tabTabs.newTab().setText("Справочник"));
            tabIndexToSection.add(2);
        }

        tabTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position < 0 || position >= tabIndexToSection.size()) return;
                showTab(tabIndexToSection.get(position));
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        if (!tabIndexToSection.isEmpty()) {
            showTab(tabIndexToSection.get(0));
        }
    }

    private void showTab(int position) {
        currentSectionIndex = position;
        invalidateOptionsMenu();

        View[] views = {viewTasks, viewShopping, viewReference};
        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            if (i == position) {
                view.setVisibility(View.VISIBLE);
                view.setAlpha(0f);
                view.animate().alpha(1f).setDuration(180).start();
            } else {
                view.setVisibility(View.GONE);
            }
        }

        if (position == 0) {
            fabAdd.setOnClickListener(v -> tasksController.showAddTaskDialog());
            tasksController.load();
        } else if (position == 1) {
            fabAdd.setOnClickListener(v -> shoppingController.showAddDialog());
            shoppingController.load();
        } else {
            fabAdd.setOnClickListener(v -> referenceController.showAddDialog());
            referenceController.load();
        }
    }

    private void showTabSettingsDialog() {
        final boolean[] checked = {
            currentTab.showTasks,
            currentTab.showShopping,
            currentTab.showReference
        };
        final String[] labels = {"Задачи", "Покупки", "Справочник"};

        new AlertDialog.Builder(this)
            .setTitle("Показывать во вкладке")
            .setMultiChoiceItems(labels, checked, (d, which, isChecked) ->
                checked[which] = isChecked)
            .setPositiveButton("Сохранить", (d, w) -> {
                if (!checked[0] && !checked[1] && !checked[2]) {
                    Toast.makeText(this, "Оставьте хотя бы один раздел",
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                currentTab.showTasks = checked[0];
                currentTab.showShopping = checked[1];
                currentTab.showReference = checked[2];
                DbExecutor.runVoid(() -> tabDao.update(currentTab), this::onTabSettingsSaved);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void onTabSettingsSaved() {
        rebuildTabLayout();
        tasksController.load();
        shoppingController.load();
        referenceController.load();
    }
}
