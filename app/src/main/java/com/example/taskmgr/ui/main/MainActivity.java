package com.example.taskmgr.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.AppSettingsDao;
import com.example.taskmgr.data.dao.SectionDao;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.data.dao.TaskDao;
import com.example.taskmgr.model.Section;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.service.NotificationScheduler;
import com.example.taskmgr.ui.common.CompleteTaskDialog;
import com.example.taskmgr.ui.common.NotificationSettingsDialog;
import com.example.taskmgr.ui.section.SectionActivity;
import com.example.taskmgr.ui.stats.StatsActivity;
import com.example.taskmgr.ui.tab.TabActivity;
import com.example.taskmgr.util.AppLock;
import com.example.taskmgr.util.DbExecutor;
import com.example.taskmgr.util.PasswordHasher;
import com.example.taskmgr.util.PinInputHelper;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int SOON_KM = 500;
    private static final String PRIVATE_SECTION_NAME = "Private";

    private RecyclerView tasksRecycler;
    private TextView emptyView;
    private TaskAdapter taskAdapter;

    private TaskDao taskDao;
    private TabDao tabDao;
    private SectionDao sectionDao;
    private AppSettingsDao settingsDao;

    private int currentMainTab = 0;
    private int loadGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        taskDao = new TaskDao(this);
        tabDao = new TabDao(this);
        sectionDao = new SectionDao(this);
        settingsDao = new AppSettingsDao(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        findViewById(R.id.btn_notification_settings).setOnClickListener(v ->
            NotificationSettingsDialog.show(this));

        // На случай, если будильники сбились (обновление, ручная очистка и т.п.)
        NotificationScheduler.scheduleAll(this);
        ensureNotificationPermissionIfNeeded();

        setupSectionPlates();

        TabLayout mainTabs = findViewById(R.id.main_tabs);
        mainTabs.addTab(mainTabs.newTab().setText("Дедлайн"));
        mainTabs.addTab(mainTabs.newTab().setText("Важные"));
        mainTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentMainTab = tab.getPosition();
                loadTasks();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        tasksRecycler = findViewById(R.id.tasks_recycler);
        emptyView = findViewById(R.id.empty_view);
        tasksRecycler.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TaskAdapter(
            this::openTabForTask,
            task -> CompleteTaskDialog.show(this, task, isAutoTask(task), t -> loadTasks())
        );
        tasksRecycler.setAdapter(taskAdapter);

        DbExecutor.run(() -> {
            try {
                seedSectionsIfEmptyBlocking();
            } catch (Exception e) {
                android.util.Log.e("TaskMGR", "seed FAILED", e);
            }
            return null;
        }, ignored -> loadTasks());
    }

    private boolean isAutoTask(Task task) {
        try {
            return tabDao.isAutoTab(task.tabId);
        } catch (Exception e) {
            return false;
        }
    }

    private void openTabForTask(Task task) {
        if (task == null) return;
        Tab tab = null;
        try {
            tab = tabDao.getById(task.tabId);
        } catch (Exception ignored) {
        }
        if (tab == null) {
            Toast.makeText(this, "Вкладка задачи не найдена", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean isAuto;
        try {
            isAuto = tabDao.isAutoTab(tab.id);
        } catch (Exception e) {
            isAuto = false;
        }

        Intent i = new Intent(this, TabActivity.class);
        i.putExtra(TabActivity.EXTRA_TAB_ID, tab.id);
        i.putExtra(TabActivity.EXTRA_TAB_NAME, tab.name);
        i.putExtra(TabActivity.EXTRA_IS_AUTO, isAuto);
        i.putExtra(TabActivity.EXTRA_TASK_ID, task.id);
        startActivity(i);
    }

    private void setupSectionPlates() {
        findViewById(R.id.plate_home).setOnClickListener(v -> openSectionByName("Дом"));
        findViewById(R.id.plate_garage).setOnClickListener(v -> openSectionByName("Гараж"));
        findViewById(R.id.plate_auto).setOnClickListener(v -> openSectionByName("Авто"));
        findViewById(R.id.plate_it).setOnClickListener(v -> openSectionByName("IT"));
        findViewById(R.id.plate_health).setOnClickListener(v -> openSectionByName("Здоровье"));
        findViewById(R.id.plate_ent).setOnClickListener(v -> openSectionByName("Развлечения"));
        findViewById(R.id.plate_private).setOnClickListener(v -> handlePrivateNavClick());
        findViewById(R.id.plate_stats).setOnClickListener(v ->
            startActivity(new Intent(this, StatsActivity.class)));
    }

    private void ensureNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return;
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
            android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) return;

        String ev = settingsDao.get(AppSettingsDao.KEY_NOTIF_EVENING_ENABLED);
        String mo = settingsDao.get(AppSettingsDao.KEY_NOTIF_MORNING_ENABLED);
        boolean eveningEnabled = "1".equals(ev) || "true".equalsIgnoreCase(ev);
        boolean morningEnabled = "1".equals(mo) || "true".equalsIgnoreCase(mo);

        if (eveningEnabled || morningEnabled) {
            requestPermissions(
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                NotificationSettingsDialog.REQ_POST_NOTIFICATIONS
            );
        }
    }

    private void seedSectionsIfEmptyBlocking() {
        List<Section> existing = sectionDao.getAll();

        boolean hasPrivate = false;
        for (Section s : existing) {
            if (s.isPrivate) {
                hasPrivate = true;
                break;
            }
        }

        if (existing.isEmpty()) {
            sectionDao.insert(new Section("Дом", "home", 1));
            sectionDao.insert(new Section("Гараж", "garage", 2));
            sectionDao.insert(new Section("Авто", "car", 3));
            sectionDao.insert(new Section("IT", "it", 4));
            sectionDao.insert(new Section("Здоровье", "health", 5));
            sectionDao.insert(new Section("Развлечения", "ent", 6));
            sectionDao.insert(new Section(PRIVATE_SECTION_NAME, null, 7, true));
            return;
        }

        if (!hasPrivate) {
            sectionDao.insert(new Section(PRIVATE_SECTION_NAME, null, 7, true));
        }
    }

    private void loadTasks() {
        final int myGeneration = ++loadGeneration;
        final int tabSnapshot = currentMainTab;
        final boolean includePrivate = AppLock.isPrivateUnlocked();
        DbExecutor.run(() -> {
            List<Tab> allTabs = tabDao.getAll();
            List<Section> allSections = sectionDao.getAll();
            Map<Long, Tab> tabMap = new HashMap<>();
            for (Tab t : allTabs) tabMap.put(t.id, t);

            Set<Long> excluded = new HashSet<>();
            if (!includePrivate) {
                for (Tab t : tabDao.getByPrivateSections()) excluded.add(t.id);
            }

            List<Task> tasks;
            if (tabSnapshot == 0) {
                List<Task> raw = taskDao.getDeadlineSoon(excluded);
                tasks = new ArrayList<>(raw.size());
                for (Task t : raw) {
                    if ("by_km".equals(t.dueType)) {
                        Tab tab = tabMap.get(t.tabId);
                        if (tab == null || tab.currentKm == null) {
                            tasks.add(t);
                        } else if (t.dueKm != null && t.dueKm - tab.currentKm <= SOON_KM) {
                            tasks.add(t);
                        }
                    } else {
                        tasks.add(t);
                    }
                }
            } else {
                tasks = taskDao.getImportant(excluded);
            }
            return new LoadResult(tasks, allTabs, allSections);
        }, result -> {
            if (myGeneration != loadGeneration) return;
            if (result == null) return;
            taskAdapter.setItems(result.tasks(), result.tabs(), result.sections());
            boolean empty = result.tasks().isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            tasksRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private void openSectionByName(String name) {
        DbExecutor.run(() -> {
            for (Section s : sectionDao.getAll()) {
                if (s.name.equals(name)) return s;
            }
            return null;
        }, found -> {
            if (found == null) {
                Toast.makeText(this, "Раздел не найден: " + name, Toast.LENGTH_SHORT).show();
                return;
            }
            openSection(found);
        });
    }

    private void handlePrivateNavClick() {
        if (!settingsDao.hasPrivatePassword()) {
            showCreatePasswordDialog(() -> {
                AppLock.setPrivateUnlocked(true);
                findPrivateSectionAndOpen();
            });
            return;
        }
        if (AppLock.isPrivateUnlocked()) {
            findPrivateSectionAndOpen();
            return;
        }
        showEnterPasswordDialog(() -> {
            AppLock.setPrivateUnlocked(true);
            findPrivateSectionAndOpen();
            loadTasks();
        });
    }

    private void findPrivateSectionAndOpen() {
        DbExecutor.run(() -> {
            for (Section s : sectionDao.getAll()) {
                if (s.isPrivate) return s;
            }
            return null;
        }, found -> {
            if (found == null) {
                Toast.makeText(this, "Приватный раздел не найден", Toast.LENGTH_SHORT).show();
                return;
            }
            openSection(found);
        });
    }

    private void openSection(Section s) {
        Intent i = new Intent(this, SectionActivity.class);
        i.putExtra(SectionActivity.EXTRA_SECTION_ID, s.id);
        i.putExtra(SectionActivity.EXTRA_SECTION_NAME, s.name);
        startActivity(i);
    }

    private void showCreatePasswordDialog(OnSuccess onSuccess) {
        View view = getLayoutInflater().inflate(R.layout.dialog_set_password, null);

        EditText c1 = view.findViewById(R.id.pin_create_1);
        EditText c2 = view.findViewById(R.id.pin_create_2);
        EditText c3 = view.findViewById(R.id.pin_create_3);
        EditText c4 = view.findViewById(R.id.pin_create_4);
        EditText f1 = view.findViewById(R.id.pin_confirm_1);
        EditText f2 = view.findViewById(R.id.pin_confirm_2);
        EditText f3 = view.findViewById(R.id.pin_confirm_3);
        EditText f4 = view.findViewById(R.id.pin_confirm_4);

        PinInputHelper createHelper = new PinInputHelper(c1, c2, c3, c4);
        PinInputHelper confirmHelper = new PinInputHelper(f1, f2, f3, f4);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("PIN для приватного раздела")
            .setMessage("Придумайте PIN из 4 цифр. Восстановить его будет нельзя.")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!createHelper.isComplete() || !confirmHelper.isComplete()) {
                    Toast.makeText(this, "Введите все 4 цифры", Toast.LENGTH_SHORT).show();
                    return;
                }
                String pwd = createHelper.getValue();
                String confirm = confirmHelper.getValue();
                if (!pwd.matches("\\d{4}")) {
                    Toast.makeText(this, "Только 4 цифры", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!pwd.equals(confirm)) {
                    Toast.makeText(this, "PIN не совпадает", Toast.LENGTH_SHORT).show();
                    confirmHelper.clear();
                    return;
                }
                final String pwdFinal = pwd;
                DbExecutor.runVoid(() -> {
                    String salt = PasswordHasher.newSaltBase64();
                    int iter = PasswordHasher.DEFAULT_ITERATIONS;
                    String hash = PasswordHasher.hashBase64(pwdFinal, salt, iter);
                    settingsDao.put(AppSettingsDao.KEY_PRIVATE_PWD_SALT, salt);
                    settingsDao.put(AppSettingsDao.KEY_PRIVATE_PWD_ITER, String.valueOf(iter));
                    settingsDao.put(AppSettingsDao.KEY_PRIVATE_PWD_HASH, hash);
                }, () -> {
                    dialog.dismiss();
                    onSuccess.run();
                });
            })
        );

        dialog.show();
        createHelper.focusFirst();
    }

    private void showEnterPasswordDialog(OnSuccess onSuccess) {
        View view = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);

        EditText p1 = view.findViewById(R.id.pin_1);
        EditText p2 = view.findViewById(R.id.pin_2);
        EditText p3 = view.findViewById(R.id.pin_3);
        EditText p4 = view.findViewById(R.id.pin_4);

        PinInputHelper helper = new PinInputHelper(p1, p2, p3, p4);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Введите PIN")
            .setView(view)
            .setPositiveButton("OK", null)
            .setNegativeButton("Отмена", null)
            .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!helper.isComplete()) {
                    Toast.makeText(this, "Введите все 4 цифры", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String pwd = helper.getValue();
                DbExecutor.run(() -> {
                    String salt = settingsDao.get(AppSettingsDao.KEY_PRIVATE_PWD_SALT);
                    String hash = settingsDao.get(AppSettingsDao.KEY_PRIVATE_PWD_HASH);
                    String iterStr = settingsDao.get(AppSettingsDao.KEY_PRIVATE_PWD_ITER);
                    int iter = PasswordHasher.DEFAULT_ITERATIONS;
                    try {
                        if (iterStr != null) iter = Integer.parseInt(iterStr);
                    } catch (Exception ignored) {
                    }
                    return PasswordHasher.verify(pwd, salt, iter, hash);
                }, ok -> {
                    if (ok != null && ok) {
                        dialog.dismiss();
                        onSuccess.run();
                    } else {
                        Toast.makeText(this, "Неверный PIN", Toast.LENGTH_SHORT).show();
                        helper.clear();
                    }
                });
            })
        );

        dialog.show();
        helper.focusFirst();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NotificationSettingsDialog.REQ_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Уведомления разрешены", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Без разрешения уведомления не придут", Toast.LENGTH_LONG).show();
            }
        }
    }

    private interface OnSuccess {
        void run();
    }

    private record LoadResult(List<Task> tasks, List<Tab> tabs, List<Section> sections) {
    }
}
