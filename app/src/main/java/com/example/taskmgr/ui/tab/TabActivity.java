package com.example.taskmgr.ui.tab;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.HistoryDao;
import com.example.taskmgr.data.dao.ReferenceDao;
import com.example.taskmgr.data.dao.SectionDao;
import com.example.taskmgr.data.dao.ShoppingDao;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.data.dao.TaskDao;
import com.example.taskmgr.model.HistoryEntry;
import com.example.taskmgr.model.ReferenceEntry;
import com.example.taskmgr.model.Section;
import com.example.taskmgr.model.ShoppingItem;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.model.Task;
import com.example.taskmgr.service.TaskService;
import com.example.taskmgr.service.TaskUrgencyComparator;
import com.example.taskmgr.ui.common.CompleteTaskDialog;
import com.example.taskmgr.util.AppLock;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class TabActivity extends AppCompatActivity {

    public static final String EXTRA_TAB_ID = "tab_id";
    public static final String EXTRA_TAB_NAME = "tab_name";
    public static final String EXTRA_IS_AUTO = "is_auto";
    public static final String EXTRA_TASK_ID = "task_id";
    private final List<Integer> tabIndexToSection = new ArrayList<>();
    private long tabId;
    private boolean isAuto;
    private long extraTaskId = -1;
    private TabDao tabDao;
    private TaskDao taskDao;
    private HistoryDao historyDao;
    private ShoppingDao shoppingDao;
    private ReferenceDao referenceDao;
    private TabLayout tabTabs;
    @SuppressWarnings("FieldCanBeLocal")
    private FrameLayout contentContainer;
    private FloatingActionButton fabAdd;
    private View viewTasks, viewShopping, viewReference;
    private Tab currentTab;
    private int currentSectionIndex = 0;
    private RecyclerView tasksRecycler;
    private TextView tasksEmpty;
    private TaskFullAdapter taskAdapter;
    private HistoryAdapter historyAdapter;
    private MaterialButton btnSortCreated, btnSortDeadline;
    private boolean showingHistory = false;
    private boolean sortByDeadline = false;
    private boolean sortAscending = false;
    private int loadGeneration = 0;

    private RecyclerView shoppingRecycler;
    private TextView shoppingEmpty, totalPrice;
    private ShoppingAdapter shoppingAdapter;
    private boolean loadingShopping = false;
    private String selectedShoppingCategory = null;
    private ChipGroup shoppingChipGroup;
    private ShoppingDao.SortMode shoppingSortMode = ShoppingDao.SortMode.CREATED;
    private boolean shoppingSortAsc = false;
    private MaterialButton btnShopSortCreated, btnShopSortPrice, btnShopSortTitle;

    private RecyclerView referenceRecycler;
    private TextView referenceEmpty;
    private ReferenceAdapter referenceAdapter;
    private boolean loadingReference = false;

    private String selectedReferenceCategory = null;
    private ChipGroup referenceChipGroup;
    private ReferenceDao.SortMode referenceSortMode = ReferenceDao.SortMode.TITLE;
    private boolean referenceSortAsc = false;
    private MaterialButton btnRefSortTitle, btnRefSortCategory;

    private static int priorityToIndex(String p) {
        if ("important".equals(p)) return 0;
        if ("whatever".equals(p)) return 2;
        return 1;
    }

    private static String indexToPriority(int idx) {
        return switch (idx) {
            case 0 -> "important";
            case 2 -> "whatever";
            default -> "normal";
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab);
        tabDao = new TabDao(this);

        tabId = getIntent().getLongExtra(EXTRA_TAB_ID, -1);
        String tabName = getIntent().getStringExtra(EXTRA_TAB_NAME);
        isAuto = getIntent().getBooleanExtra(EXTRA_IS_AUTO, false);
        extraTaskId = getIntent().getLongExtra(EXTRA_TASK_ID, -1);

        Tab t = tabDao.getById(tabId);
        if (t == null) {
            finish();
            return;
        }
        currentTab = t;

        SectionDao sDao = new SectionDao(this);
        Section s = sDao.getById(t.sectionId);
        if (s != null && s.isPrivate && !AppLock.isPrivateUnlocked()) {
            finish();
            return;
        }

        taskDao = new TaskDao(this);
        historyDao = new HistoryDao(this);
        shoppingDao = new ShoppingDao(this);
        referenceDao = new ReferenceDao(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(tabName != null ? tabName : "Вкладка");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tabTabs = findViewById(R.id.tab_tabs);
        contentContainer = findViewById(R.id.content_container);
        fabAdd = findViewById(R.id.fab_add);

        viewTasks = getLayoutInflater().inflate(R.layout.fragment_tasks, contentContainer, false);
        viewShopping = getLayoutInflater().inflate(R.layout.fragment_shopping, contentContainer, false);
        viewReference = getLayoutInflater().inflate(R.layout.fragment_reference, contentContainer, false);

        contentContainer.addView(viewTasks);
        contentContainer.addView(viewShopping);
        contentContainer.addView(viewReference);

        setupTasksView();
        setupShoppingView();
        setupReferenceView();

        rebuildTabLayout();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tab, menu);
        android.view.MenuItem historyItem = menu.findItem(R.id.action_toggle_history);
        if (historyItem != null) {
            boolean visible = (currentSectionIndex == 0);
            historyItem.setVisible(visible);
            if (visible) {
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
            if (currentSectionIndex == 0) {
                setTaskMode(!showingHistory);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void pickDate(EditText target) {
        target.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            long existing = parseDateMillis(target.getText().toString());
            if (existing > 0) cal.setTimeInMillis(existing);
            new DatePickerDialog(this, (dp, year, month, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, day, 0, 0, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                target.setText(formatDateForInput(chosen.getTimeInMillis()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private long parseDateMillis(String s) {
        Long v = parseDate(s);
        return v != null ? v : 0L;
    }

    private void setupTasksView() {
        tasksRecycler = viewTasks.findViewById(R.id.tasks_recycler);
        tasksEmpty = viewTasks.findViewById(R.id.tasks_empty);
        btnSortCreated = viewTasks.findViewById(R.id.btn_sort_created);
        btnSortDeadline = viewTasks.findViewById(R.id.btn_sort_deadline);

        tasksRecycler.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TaskFullAdapter(this::showTaskActionsDialog, this::showTaskActionsDialog);
        historyAdapter = new HistoryAdapter(this::showHistoryActionsDialog);
        tasksRecycler.setAdapter(taskAdapter);

        btnSortCreated.setOnClickListener(v -> toggleTaskSort(false));
        btnSortDeadline.setOnClickListener(v -> toggleTaskSort(true));

        updateTaskButtons();
        loadTasks();
    }

    private void setTaskMode(boolean history) {
        if (showingHistory == history) return;
        showingHistory = history;
        updateTaskButtons();
        loadTasks();
    }

    private void toggleTaskSort(boolean byDeadline) {
        if (showingHistory) return;
        if (sortByDeadline == byDeadline) {
            sortAscending = !sortAscending;
        } else {
            sortByDeadline = byDeadline;
            sortAscending = false;
        }
        updateTaskButtons();
        loadTasks();
    }

    private void updateTaskButtons() {
        int accent = ContextCompat.getColor(this, R.color.neon_green);
        int accentDim = ContextCompat.getColor(this, R.color.neon_green_dim);
        int inactiveStroke = ContextCompat.getColor(this, R.color.divider_strong);
        int inactiveText = ContextCompat.getColor(this, R.color.text_secondary);

        boolean sortEnabled = !showingHistory;
        boolean createdActive = sortEnabled && !sortByDeadline;
        boolean deadlineActive = sortEnabled && sortByDeadline;

        applySeg(btnSortCreated, createdActive, accent, accentDim, inactiveStroke, inactiveText);
        applySeg(btnSortDeadline, deadlineActive, accent, accentDim, inactiveStroke, inactiveText);

        btnSortCreated.setText("Создание");
        btnSortDeadline.setText("Дедлайн");

        if (createdActive) {
            setSortIcon(btnSortCreated, sortAscending);
        } else {
            btnSortCreated.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
        if (deadlineActive) {
            setSortIcon(btnSortDeadline, sortAscending);
        } else {
            btnSortDeadline.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }

        btnSortCreated.setEnabled(sortEnabled);
        btnSortDeadline.setEnabled(sortEnabled);

        invalidateOptionsMenu();
    }

    private void setSortIcon(MaterialButton b, boolean asc) {
        int iconRes = asc ? R.drawable.ic_sort_asc : R.drawable.ic_sort_desc;
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        b.setCompoundDrawablePadding(2);
    }

    private void applySeg(MaterialButton b, boolean checked,
                          int accent, int accentDim, int inactiveStroke,
                          int inactiveText) {
        b.setChecked(checked);
        if (checked) {
            b.setStrokeColor(ColorStateList.valueOf(accent));
            b.setTextColor(accent);
            b.setBackgroundTintList(ColorStateList.valueOf(accentDim & 0x33FFFFFF));
        } else {
            b.setStrokeColor(ColorStateList.valueOf(inactiveStroke));
            b.setTextColor(inactiveText);
            b.setBackgroundTintList(ColorStateList.valueOf(0x00000000));
        }
    }

    private void loadTasks() {
        final int myGeneration = ++loadGeneration;
        final boolean history = showingHistory;
        final boolean sortDeadline = sortByDeadline;
        final boolean sortAsc = sortAscending;
        final long pendingTaskId = extraTaskId;
        DbExecutor.run(() -> {
            if (history) {
                List<Task> allTasks = taskDao.getAllByTab(tabId);
                List<HistoryEntry> entries = new ArrayList<>();
                for (Task t : allTasks) entries.addAll(historyDao.getByTask(t.id));
                entries.sort((a, b) -> Long.compare(b.completedAt, a.completedAt));
                return new TasksResult(entries, allTasks, null);
            } else {
                List<Task> activeTasks = taskDao.getByTab(tabId);
                Tab currentTab = tabDao.getById(tabId);

                if (sortDeadline && currentTab != null) {
                    Map<Long, Tab> tabsById = new HashMap<>();
                    tabsById.put(currentTab.id, currentTab);
                    activeTasks.sort(new TaskUrgencyComparator(tabsById));
                }
                if (sortAsc) {
                    java.util.Collections.reverse(activeTasks);
                }
                return new TasksResult(null, activeTasks, currentTab);
            }
        }, result -> {
            if (myGeneration != loadGeneration) return;
            if (result == null) return;
            if (history) {
                List<HistoryEntry> entries = result.entries();
                List<Task> allTasks = result.tasks();
                tasksRecycler.setAdapter(historyAdapter);
                historyAdapter.setItems(entries, allTasks);
                boolean empty = entries.isEmpty();
                tasksEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                tasksRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            } else {
                List<Task> tasks = result.tasks();
                Tab currentTab = result.tab();
                tasksRecycler.setAdapter(taskAdapter);
                taskAdapter.setCurrentTab(currentTab);
                taskAdapter.setItems(tasks);
                boolean empty = tasks.isEmpty();
                tasksEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                tasksRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);

                if (pendingTaskId > 0) {
                    extraTaskId = -1;
                    final int pos = taskAdapter.positionOfTask(pendingTaskId);
                    if (pos >= 0) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            tasksRecycler.scrollToPosition(pos);
                            taskAdapter.setHighlightedTaskId(pendingTaskId);
                            new Handler(Looper.getMainLooper()).postDelayed(
                                taskAdapter::clearHighlight, 1200);
                        }, 150);
                    }
                }
            }
        });
    }

    private void showHistoryActionsDialog(HistoryEntry entry, Task task) {
        if (entry == null) return;
        String title = (task != null) ? task.title : ("Задача #" + entry.taskId);
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(new String[]{"Вернуть в задачи"}, (d, which) -> {
                if (which == 0) {
                    final long taskId = entry.taskId;
                    final long historyId = entry.id;
                    DbExecutor.runVoid(() -> {
                        TaskService service = new TaskService(this);
                        service.revertCompletedTask(taskId, historyId);
                    }, this::loadTasks);
                }
            })
            .show();
    }

    private void showTaskActionsDialog(Task task) {
        String[] actions = {"Выполнено", "Редактировать", "Удалить"};
        new AlertDialog.Builder(this)
            .setTitle(task.title)
            .setItems(actions, (d, which) -> {
                if (which == 0) {
                    CompleteTaskDialog.show(this, task, isAuto, t -> loadTasks());
                } else if (which == 1) {
                    showEditTaskDialog(task);
                } else {
                    DbExecutor.runVoid(() -> taskDao.delete(task.id), this::loadTasks);
                }
            })
            .show();
    }

    private void showEditTaskDialog(Task task) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        EditText inputTitle = view.findViewById(R.id.input_title);
        EditText inputDescription = view.findViewById(R.id.input_description);
        Spinner spinnerRepeatRule = view.findViewById(R.id.spinner_repeat_rule);
        EditText inputDueDate = view.findViewById(R.id.input_due_date);
        EditText inputDueFrom = view.findViewById(R.id.input_due_from);
        EditText inputDueTo = view.findViewById(R.id.input_due_to);
        EditText inputDueKm = view.findViewById(R.id.input_due_km);
        EditText inputRepeatKm = view.findViewById(R.id.input_repeat_km);
        EditText inputRepeatMonths = view.findViewById(R.id.input_repeat_months);

        MaterialButton btnPriorityImportant = view.findViewById(R.id.btn_priority_important);
        MaterialButton btnPriorityNormal = view.findViewById(R.id.btn_priority_normal);
        MaterialButton btnPriorityWhatever = view.findViewById(R.id.btn_priority_whatever);
        MaterialButton btnDueNone = view.findViewById(R.id.btn_due_none);
        MaterialButton btnDueExact = view.findViewById(R.id.btn_due_exact);
        MaterialButton btnDueRange = view.findViewById(R.id.btn_due_range);
        MaterialButton btnDueKm = view.findViewById(R.id.btn_due_km);

        pickDate(inputDueDate);
        pickDate(inputDueFrom);
        pickDate(inputDueTo);

        final int[] priorityIndex = {priorityToIndex(task.priority)};
        final List<MaterialButton> priorityButtons = new ArrayList<>();
        priorityButtons.add(btnPriorityImportant);
        priorityButtons.add(btnPriorityNormal);
        priorityButtons.add(btnPriorityWhatever);
        applySegGroup(priorityButtons, priorityIndex[0]);
        btnPriorityImportant.setOnClickListener(v -> {
            priorityIndex[0] = 0;
            applySegGroup(priorityButtons, 0);
        });
        btnPriorityNormal.setOnClickListener(v -> {
            priorityIndex[0] = 1;
            applySegGroup(priorityButtons, 1);
        });
        btnPriorityWhatever.setOnClickListener(v -> {
            priorityIndex[0] = 2;
            applySegGroup(priorityButtons, 2);
        });

        final String[] dueTypes = isAuto
            ? new String[]{"none", "exact_date", "range", "by_km"}
            : new String[]{"none", "exact_date", "range"};
        final List<MaterialButton> dueButtons = new ArrayList<>();
        dueButtons.add(btnDueNone);
        dueButtons.add(btnDueExact);
        dueButtons.add(btnDueRange);
        if (isAuto) dueButtons.add(btnDueKm);
        else btnDueKm.setVisibility(View.GONE);

        int initialDue = 0;
        for (int i = 0; i < dueTypes.length; i++) {
            if (dueTypes[i].equals(task.dueType)) {
                initialDue = i;
                break;
            }
        }
        final int[] dueIndex = {initialDue};
        applySegGroup(dueButtons, dueIndex[0]);

        Runnable refreshDueFields = () -> {
            String type = dueTypes[dueIndex[0]];
            inputDueDate.setVisibility("exact_date".equals(type) ? View.VISIBLE : View.GONE);
            inputDueFrom.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
            inputDueTo.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
            inputDueKm.setVisibility("by_km".equals(type) ? View.VISIBLE : View.GONE);
        };
        btnDueNone.setOnClickListener(v -> {
            dueIndex[0] = 0;
            applySegGroup(dueButtons, 0);
            refreshDueFields.run();
        });
        btnDueExact.setOnClickListener(v -> {
            dueIndex[0] = 1;
            applySegGroup(dueButtons, 1);
            refreshDueFields.run();
        });
        btnDueRange.setOnClickListener(v -> {
            dueIndex[0] = 2;
            applySegGroup(dueButtons, 2);
            refreshDueFields.run();
        });
        if (isAuto) {
            btnDueKm.setOnClickListener(v -> {
                dueIndex[0] = 3;
                applySegGroup(dueButtons, 3);
                refreshDueFields.run();
            });
        }
        refreshDueFields.run();

        inputTitle.setText(task.title);
        if (task.description != null) inputDescription.setText(task.description);
        if ("exact_date".equals(task.dueType) && task.dueDate != null) {
            inputDueDate.setText(formatDateForInput(task.dueDate));
        } else if ("range".equals(task.dueType)) {
            if (task.dueFrom != null) inputDueFrom.setText(formatDateForInput(task.dueFrom));
            if (task.dueTo != null) inputDueTo.setText(formatDateForInput(task.dueTo));
        } else if ("by_km".equals(task.dueType) && task.dueKm != null) {
            inputDueKm.setText(String.valueOf(task.dueKm));
        }

        final String[] repeatRules;
        if (isAuto) {
            repeatRules = new String[]{"none", "by_km", "by_months", "by_km_or_months"};
            setSpinner(spinnerRepeatRule,
                new String[]{"без повтора", "по пробегу", "по месяцам", "по пробегу или месяцам"});
            if (task.repeatRule != null) {
                int repIdx = 0;
                for (int i = 0; i < repeatRules.length; i++) {
                    if (repeatRules[i].equals(task.repeatRule)) {
                        repIdx = i;
                        break;
                    }
                }
                spinnerRepeatRule.setSelection(repIdx);
                if ((repIdx == 1 || repIdx == 3) && task.repeatIntervalKm != null)
                    inputRepeatKm.setText(String.valueOf(task.repeatIntervalKm));
                if ((repIdx == 2 || repIdx == 3) && task.repeatIntervalMonths != null)
                    inputRepeatMonths.setText(String.valueOf(task.repeatIntervalMonths));
            }
            spinnerRepeatRule.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                    String rule = repeatRules[pos];
                    inputRepeatKm.setVisibility(
                        ("by_km".equals(rule) || "by_km_or_months".equals(rule)) ? View.VISIBLE : View.GONE);
                    inputRepeatMonths.setVisibility(
                        ("by_months".equals(rule) || "by_km_or_months".equals(rule)) ? View.VISIBLE : View.GONE);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        } else {
            repeatRules = new String[]{"none"};
            spinnerRepeatRule.setVisibility(View.GONE);
            inputRepeatKm.setVisibility(View.GONE);
            inputRepeatMonths.setVisibility(View.GONE);
            View repeatParent = (View) spinnerRepeatRule.getParent();
            if (repeatParent instanceof android.view.ViewGroup) {
                for (int i = 0; i < ((android.view.ViewGroup) repeatParent).getChildCount(); i++) {
                    View child = ((android.view.ViewGroup) repeatParent).getChildAt(i);
                    if (child instanceof TextView
                        && "Повтор".contentEquals(((TextView) child).getText())) {
                        child.setVisibility(View.GONE);
                    }
                }
            }
        }

        new AlertDialog.Builder(this)
            .setTitle("Редактировать задачу")
            .setView(view)
            .setPositiveButton("Сохранить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                if (TextUtils.isEmpty(title)) {
                    Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
                    return;
                }
                task.title = title;
                task.priority = indexToPriority(priorityIndex[0]);
                String desc = inputDescription.getText().toString().trim();
                task.description = desc.isEmpty() ? null : desc;

                task.dueType = dueTypes[dueIndex[0]];
                task.dueDate = null;
                task.dueFrom = null;
                task.dueTo = null;
                task.dueKm = null;
                if ("exact_date".equals(task.dueType)) {
                    task.dueDate = parseDate(inputDueDate.getText().toString());
                } else if ("range".equals(task.dueType)) {
                    task.dueFrom = parseDate(inputDueFrom.getText().toString());
                    task.dueTo = parseDate(inputDueTo.getText().toString());
                } else if ("by_km".equals(task.dueType)) {
                    task.dueKm = parseIntOrNull(inputDueKm.getText().toString());
                }

                if (isAuto) {
                    int repPos = spinnerRepeatRule.getSelectedItemPosition();
                    task.repeatRule = repeatRules[repPos];
                    task.repeatIntervalKm = null;
                    task.repeatIntervalMonths = null;
                    if ("by_km".equals(task.repeatRule) || "by_km_or_months".equals(task.repeatRule))
                        task.repeatIntervalKm = parseIntOrNull(inputRepeatKm.getText().toString());
                    if ("by_months".equals(task.repeatRule) || "by_km_or_months".equals(task.repeatRule))
                        task.repeatIntervalMonths = parseIntOrNull(inputRepeatMonths.getText().toString());
                } else {
                    task.repeatRule = "none";
                    task.repeatIntervalKm = null;
                    task.repeatIntervalMonths = null;
                }

                DbExecutor.runVoid(() -> taskDao.update(task), this::loadTasks);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private String formatDateForInput(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(millis));
    }

    private void setupShoppingView() {
        shoppingRecycler = viewShopping.findViewById(R.id.shopping_recycler);
        shoppingEmpty = viewShopping.findViewById(R.id.shopping_empty);
        totalPrice = viewShopping.findViewById(R.id.total_price);
        shoppingChipGroup = viewShopping.findViewById(R.id.shopping_chip_group);

        btnShopSortCreated = viewShopping.findViewById(R.id.btn_shop_sort_created);
        btnShopSortPrice = viewShopping.findViewById(R.id.btn_shop_sort_price);
        btnShopSortTitle = viewShopping.findViewById(R.id.btn_shop_sort_title);

        shoppingRecycler.setLayoutManager(new LinearLayoutManager(this));
        shoppingAdapter = new ShoppingAdapter(
            (item, bought) -> {
                DbExecutor.runVoid(() -> shoppingDao.setBought(item.id, bought),
                    this::loadShopping);
            },
            this::showShoppingActionsDialog
        );
        shoppingRecycler.setAdapter(shoppingAdapter);

        shoppingChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                if (selectedShoppingCategory != null) {
                    selectedShoppingCategory = null;
                    loadShopping();
                }
                return;
            }
            int chipId = checkedIds.get(0);
            Chip chip = group.findViewById(chipId);
            String newCategory = (chip != null && chip.getTag() != null)
                ? chip.getTag().toString()
                : null;
            if (!Objects.equals(selectedShoppingCategory, newCategory)) {
                selectedShoppingCategory = newCategory;
                loadShopping();
            }
        });

        btnShopSortCreated.setOnClickListener(v -> toggleShoppingSort(ShoppingDao.SortMode.CREATED));
        btnShopSortPrice.setOnClickListener(v -> toggleShoppingSort(ShoppingDao.SortMode.PRICE));
        btnShopSortTitle.setOnClickListener(v -> toggleShoppingSort(ShoppingDao.SortMode.TITLE));

        updateShoppingSortButtons();
        loadShopping();
    }

    private void toggleShoppingSort(ShoppingDao.SortMode mode) {
        if (shoppingSortMode == mode) {
            shoppingSortAsc = !shoppingSortAsc;
        } else {
            shoppingSortMode = mode;
            shoppingSortAsc = false;
        }
        updateShoppingSortButtons();
        loadShopping();
    }

    private void updateShoppingSortButtons() {
        int accent = ContextCompat.getColor(this, R.color.neon_green);
        int accentDim = ContextCompat.getColor(this, R.color.neon_green_dim);
        int inactiveStroke = ContextCompat.getColor(this, R.color.divider_strong);
        int inactiveText = ContextCompat.getColor(this, R.color.text_secondary);

        applyShoppingSeg(btnShopSortCreated, shoppingSortMode == ShoppingDao.SortMode.CREATED,
            accent, accentDim, inactiveStroke, inactiveText);
        applyShoppingSeg(btnShopSortPrice, shoppingSortMode == ShoppingDao.SortMode.PRICE,
            accent, accentDim, inactiveStroke, inactiveText);
        applyShoppingSeg(btnShopSortTitle, shoppingSortMode == ShoppingDao.SortMode.TITLE,
            accent, accentDim, inactiveStroke, inactiveText);

        boolean asc = shoppingSortAsc;
        if (shoppingSortMode == ShoppingDao.SortMode.CREATED) setSortIcon(btnShopSortCreated, asc);
        else btnShopSortCreated.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        if (shoppingSortMode == ShoppingDao.SortMode.PRICE) setSortIcon(btnShopSortPrice, asc);
        else btnShopSortPrice.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        if (shoppingSortMode == ShoppingDao.SortMode.TITLE) setSortIcon(btnShopSortTitle, asc);
        else btnShopSortTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
    }

    private void applyShoppingSeg(MaterialButton b, boolean checked,
                                  int accent, int accentDim, int inactiveStroke, int inactiveText) {
        b.setChecked(checked);
        if (checked) {
            b.setStrokeColor(ColorStateList.valueOf(accent));
            b.setTextColor(accent);
            b.setBackgroundTintList(ColorStateList.valueOf(accentDim & 0x33FFFFFF));
        } else {
            b.setStrokeColor(ColorStateList.valueOf(inactiveStroke));
            b.setTextColor(inactiveText);
            b.setBackgroundTintList(ColorStateList.valueOf(0x00000000));
        }
    }

    private void showShoppingActionsDialog(ShoppingItem item) {
        new AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(new String[]{"Редактировать", "Удалить"}, (d, which) -> {
                if (which == 0) showEditShoppingDialog(item);
                else DbExecutor.runVoid(() -> shoppingDao.delete(item.id), this::loadShopping);
            })
            .show();
    }

    private void showEditShoppingDialog(ShoppingItem item) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_shopping, null);
        EditText inputTitle = view.findViewById(R.id.input_shopping_title);
        EditText inputPrice = view.findViewById(R.id.input_shopping_price);
        EditText inputCategory = view.findViewById(R.id.input_shopping_category);
        inputTitle.setText(item.title);
        if (item.price != null) inputPrice.setText(String.valueOf(item.price));
        if (item.category != null) inputCategory.setText(item.category);

        new AlertDialog.Builder(this)
            .setTitle("Редактировать покупку")
            .setView(view)
            .setPositiveButton("Сохранить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                if (TextUtils.isEmpty(title)) {
                    Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
                    return;
                }
                item.title = title;
                String priceStr = inputPrice.getText().toString().trim();
                item.price = priceStr.isEmpty() ? null : Double.parseDouble(priceStr);
                String category = inputCategory.getText().toString().trim();
                item.category = category.isEmpty() ? null : category;
                DbExecutor.runVoid(() -> shoppingDao.update(item), this::loadShopping);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void loadShopping() {
        if (loadingShopping) return;
        loadingShopping = true;
        final String categoryFilter = selectedShoppingCategory;
        final ShoppingDao.SortMode mode = shoppingSortMode;
        final boolean asc = shoppingSortAsc;
        DbExecutor.run(() -> {
            List<String> categories = shoppingDao.getCategories(tabId);
            List<ShoppingItem> items = shoppingDao.getByTabSorted(tabId, categoryFilter, mode, asc);
            double total = shoppingDao.getTotalPrice(tabId, categoryFilter);
            return new ShoppingResult(categories, items, total);
        }, result -> {
            loadingShopping = false;
            if (result == null) return;
            List<String> categories = result.categories();
            List<ShoppingItem> items = result.items();

            rebuildShoppingChips(categories);

            shoppingAdapter.setItems(items);
            boolean empty = items.isEmpty();
            shoppingEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            shoppingRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            totalPrice.setText(String.format(Locale.getDefault(), "Итого: %.2f", result.total()));
        });
    }

    private void setupReferenceView() {
        referenceRecycler = viewReference.findViewById(R.id.reference_recycler);
        referenceEmpty = viewReference.findViewById(R.id.reference_empty);
        referenceChipGroup = viewReference.findViewById(R.id.reference_chip_group);

        btnRefSortTitle = viewReference.findViewById(R.id.btn_ref_sort_title);
        btnRefSortCategory = viewReference.findViewById(R.id.btn_ref_sort_category);

        referenceRecycler.setLayoutManager(new LinearLayoutManager(this));
        referenceAdapter = new ReferenceAdapter(this::showReferenceActionsDialog);
        referenceRecycler.setAdapter(referenceAdapter);

        referenceChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                if (selectedReferenceCategory != null) {
                    selectedReferenceCategory = null;
                    loadReference();
                }
                return;
            }
            int chipId = checkedIds.get(0);
            Chip chip = group.findViewById(chipId);
            String newCategory = (chip != null && chip.getTag() != null)
                ? chip.getTag().toString()
                : null;
            if (!Objects.equals(selectedReferenceCategory, newCategory)) {
                selectedReferenceCategory = newCategory;
                loadReference();
            }
        });

        btnRefSortTitle.setOnClickListener(v -> toggleReferenceSort(ReferenceDao.SortMode.TITLE));
        btnRefSortCategory.setOnClickListener(v -> toggleReferenceSort(ReferenceDao.SortMode.CATEGORY));

        updateReferenceSortButtons();
        loadReference();
    }

    private void toggleReferenceSort(ReferenceDao.SortMode mode) {
        if (referenceSortMode == mode) {
            referenceSortAsc = !referenceSortAsc;
        } else {
            referenceSortMode = mode;
            referenceSortAsc = false;
        }
        updateReferenceSortButtons();
        loadReference();
    }

    private void updateReferenceSortButtons() {
        int accent = ContextCompat.getColor(this, R.color.neon_green);
        int accentDim = ContextCompat.getColor(this, R.color.neon_green_dim);
        int inactiveStroke = ContextCompat.getColor(this, R.color.divider_strong);
        int inactiveText = ContextCompat.getColor(this, R.color.text_secondary);

        applyShoppingSeg(btnRefSortTitle, referenceSortMode == ReferenceDao.SortMode.TITLE,
            accent, accentDim, inactiveStroke, inactiveText);
        applyShoppingSeg(btnRefSortCategory, referenceSortMode == ReferenceDao.SortMode.CATEGORY,
            accent, accentDim, inactiveStroke, inactiveText);

        if (referenceSortMode == ReferenceDao.SortMode.TITLE)
            setSortIcon(btnRefSortTitle, referenceSortAsc);
        else btnRefSortTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        if (referenceSortMode == ReferenceDao.SortMode.CATEGORY)
            setSortIcon(btnRefSortCategory, referenceSortAsc);
        else btnRefSortCategory.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
    }

    private void rebuildShoppingChips(List<String> categories) {
        shoppingChipGroup.setOnCheckedStateChangeListener(null);
        shoppingChipGroup.removeAllViews();

        if (selectedShoppingCategory != null
            && !categories.contains(selectedShoppingCategory)) {
            selectedShoppingCategory = null;
        }

        Chip allChip = buildChip("Все", null);
        allChip.setChecked(selectedShoppingCategory == null);
        shoppingChipGroup.addView(allChip);

        for (String cat : categories) {
            Chip c = buildChip(cat, cat);
            c.setChecked(Objects.equals(selectedShoppingCategory, cat));
            shoppingChipGroup.addView(c);
        }

        shoppingChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                if (selectedShoppingCategory != null) {
                    selectedShoppingCategory = null;
                    loadShopping();
                }
                return;
            }
            int chipId = checkedIds.get(0);
            Chip chip = group.findViewById(chipId);
            String newCategory = (chip != null && chip.getTag() != null)
                ? chip.getTag().toString()
                : null;
            if (!Objects.equals(selectedShoppingCategory, newCategory)) {
                selectedShoppingCategory = newCategory;
                loadShopping();
            }
        });
    }

    private void rebuildReferenceChips(List<String> categories) {
        referenceChipGroup.setOnCheckedStateChangeListener(null);
        referenceChipGroup.removeAllViews();

        if (selectedReferenceCategory != null
            && !categories.contains(selectedReferenceCategory)) {
            selectedReferenceCategory = null;
        }

        Chip allChip = buildChip("Все", null);
        allChip.setChecked(selectedReferenceCategory == null);
        referenceChipGroup.addView(allChip);

        for (String cat : categories) {
            Chip c = buildChip(cat, cat);
            c.setChecked(Objects.equals(selectedReferenceCategory, cat));
            referenceChipGroup.addView(c);
        }

        referenceChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                if (selectedReferenceCategory != null) {
                    selectedReferenceCategory = null;
                    loadReference();
                }
                return;
            }
            int chipId = checkedIds.get(0);
            Chip chip = group.findViewById(chipId);
            String newCategory = (chip != null && chip.getTag() != null)
                ? chip.getTag().toString()
                : null;
            if (!Objects.equals(selectedReferenceCategory, newCategory)) {
                selectedReferenceCategory = newCategory;
                loadReference();
            }
        });
    }

    private Chip buildChip(String label, String tag) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setTag(tag);
        return chip;
    }

    private void showReferenceActionsDialog(ReferenceEntry item) {
        new AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(new String[]{"Редактировать", "Удалить"}, (d, which) -> {
                if (which == 0) showEditReferenceDialog(item);
                else
                    DbExecutor.runVoid(() -> referenceDao.delete(item.id), this::loadReference);
            })
            .show();
    }

    private void showEditReferenceDialog(ReferenceEntry item) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_reference, null);
        EditText inputTitle = view.findViewById(R.id.input_ref_title);
        EditText inputValue = view.findViewById(R.id.input_ref_value);
        EditText inputDescription = view.findViewById(R.id.input_ref_description);
        EditText inputCategory = view.findViewById(R.id.input_ref_category);
        inputTitle.setText(item.title);
        inputValue.setText(item.value);
        if (item.description != null) inputDescription.setText(item.description);
        if (item.category != null) inputCategory.setText(item.category);

        new AlertDialog.Builder(this)
            .setTitle("Редактировать запись")
            .setView(view)
            .setPositiveButton("Сохранить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                String value = inputValue.getText().toString().trim();
                String description = inputDescription.getText().toString().trim();
                String category = inputCategory.getText().toString().trim();
                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(value)) {
                    Toast.makeText(this, "Название и значение обязательны", Toast.LENGTH_SHORT).show();
                    return;
                }
                item.title = title;
                item.value = value;
                item.description = description.isEmpty() ? null : description;
                item.category = category.isEmpty() ? null : category;
                DbExecutor.runVoid(() -> referenceDao.update(item), this::loadReference);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void loadReference() {
        if (loadingReference) return;
        loadingReference = true;
        final String categoryFilter = selectedReferenceCategory;
        final ReferenceDao.SortMode mode = referenceSortMode;
        final boolean asc = referenceSortAsc;
        DbExecutor.run(() -> {
            List<String> categories = referenceDao.getCategories(tabId);
            List<ReferenceEntry> items = referenceDao.getByTabSorted(tabId, categoryFilter, mode, asc);
            return new ReferenceResult(categories, items);
        }, result -> {
            loadingReference = false;
            if (result == null) return;
            List<String> categories = result.categories();
            List<ReferenceEntry> items = result.items();

            rebuildReferenceChips(categories);

            referenceAdapter.setItems(items);
            boolean empty = items.isEmpty();
            referenceEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            referenceRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private void rebuildTabLayout() {
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

        tabTabs.clearOnTabSelectedListeners();
        tabTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tabIndexToSection.get(tab.getPosition()));
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

    private void showTabSettingsDialog() {
        final boolean[] checked = {
            currentTab.showTasks,
            currentTab.showShopping,
            currentTab.showReference
        };
        final String[] labels = {"Задачи", "Покупки", "Справочник"};

        new AlertDialog.Builder(this)
            .setTitle("Показывать во вкладке")
            .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                checked[which] = isChecked;
            })
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
        loadTasks();
        loadShopping();
        loadReference();
    }

    private void showTab(int position) {
        currentSectionIndex = position;
        invalidateOptionsMenu();

        View[] views = {viewTasks, viewShopping, viewReference};
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            if (i == position) {
                v.setVisibility(View.VISIBLE);
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(180).start();
            } else {
                v.setVisibility(View.GONE);
            }
        }

        if (position == 0) {
            fabAdd.setOnClickListener(v -> showAddTaskDialog());
            loadTasks();
        } else if (position == 1) {
            fabAdd.setOnClickListener(v -> showAddShoppingDialog());
            loadShopping();
        } else {
            fabAdd.setOnClickListener(v -> showAddReferenceDialog());
            loadReference();
        }
    }

    private void applySegGroup(List<MaterialButton> buttons, int selectedIndex) {
        int accent = ContextCompat.getColor(this, R.color.neon_green);
        int accentDim = ContextCompat.getColor(this, R.color.neon_green_dim);
        int inactiveStroke = ContextCompat.getColor(this, R.color.divider_strong);
        int inactiveText = ContextCompat.getColor(this, R.color.text_secondary);

        for (int i = 0; i < buttons.size(); i++) {
            MaterialButton b = buttons.get(i);
            boolean checked = (i == selectedIndex);
            b.setChecked(checked);
            if (checked) {
                b.setStrokeColor(ColorStateList.valueOf(accent));
                b.setTextColor(accent);
                b.setBackgroundTintList(ColorStateList.valueOf(accentDim & 0x33FFFFFF));
            } else {
                b.setStrokeColor(ColorStateList.valueOf(inactiveStroke));
                b.setTextColor(inactiveText);
                b.setBackgroundTintList(ColorStateList.valueOf(0x00000000));
            }
        }
    }

    private void showAddTaskDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        EditText inputTitle = view.findViewById(R.id.input_title);
        EditText inputDescription = view.findViewById(R.id.input_description);
        Spinner spinnerRepeatRule = view.findViewById(R.id.spinner_repeat_rule);
        EditText inputDueDate = view.findViewById(R.id.input_due_date);
        EditText inputDueFrom = view.findViewById(R.id.input_due_from);
        EditText inputDueTo = view.findViewById(R.id.input_due_to);
        EditText inputDueKm = view.findViewById(R.id.input_due_km);
        EditText inputRepeatKm = view.findViewById(R.id.input_repeat_km);
        EditText inputRepeatMonths = view.findViewById(R.id.input_repeat_months);

        MaterialButton btnPriorityImportant = view.findViewById(R.id.btn_priority_important);
        MaterialButton btnPriorityNormal = view.findViewById(R.id.btn_priority_normal);
        MaterialButton btnPriorityWhatever = view.findViewById(R.id.btn_priority_whatever);
        MaterialButton btnDueNone = view.findViewById(R.id.btn_due_none);
        MaterialButton btnDueExact = view.findViewById(R.id.btn_due_exact);
        MaterialButton btnDueRange = view.findViewById(R.id.btn_due_range);
        MaterialButton btnDueKm = view.findViewById(R.id.btn_due_km);

        pickDate(inputDueDate);
        pickDate(inputDueFrom);
        pickDate(inputDueTo);

        final int[] priorityIndex = {1};
        final List<MaterialButton> priorityButtons = new ArrayList<>();
        priorityButtons.add(btnPriorityImportant);
        priorityButtons.add(btnPriorityNormal);
        priorityButtons.add(btnPriorityWhatever);
        applySegGroup(priorityButtons, priorityIndex[0]);
        btnPriorityImportant.setOnClickListener(v -> {
            priorityIndex[0] = 0;
            applySegGroup(priorityButtons, priorityIndex[0]);
        });
        btnPriorityNormal.setOnClickListener(v -> {
            priorityIndex[0] = 1;
            applySegGroup(priorityButtons, priorityIndex[0]);
        });
        btnPriorityWhatever.setOnClickListener(v -> {
            priorityIndex[0] = 2;
            applySegGroup(priorityButtons, priorityIndex[0]);
        });

        final String[] dueTypes = isAuto
            ? new String[]{"none", "exact_date", "range", "by_km"}
            : new String[]{"none", "exact_date", "range"};
        final List<MaterialButton> dueButtons = new ArrayList<>();
        dueButtons.add(btnDueNone);
        dueButtons.add(btnDueExact);
        dueButtons.add(btnDueRange);
        if (isAuto) dueButtons.add(btnDueKm);
        else btnDueKm.setVisibility(View.GONE);

        final int[] dueIndex = {0};
        applySegGroup(dueButtons, dueIndex[0]);
        Runnable refreshDueFields = () -> {
            String type = dueTypes[dueIndex[0]];
            inputDueDate.setVisibility("exact_date".equals(type) ? View.VISIBLE : View.GONE);
            inputDueFrom.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
            inputDueTo.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
            inputDueKm.setVisibility("by_km".equals(type) ? View.VISIBLE : View.GONE);
        };
        btnDueNone.setOnClickListener(v -> {
            dueIndex[0] = 0;
            applySegGroup(dueButtons, 0);
            refreshDueFields.run();
        });
        btnDueExact.setOnClickListener(v -> {
            dueIndex[0] = 1;
            applySegGroup(dueButtons, 1);
            refreshDueFields.run();
        });
        btnDueRange.setOnClickListener(v -> {
            dueIndex[0] = 2;
            applySegGroup(dueButtons, 2);
            refreshDueFields.run();
        });
        if (isAuto) {
            btnDueKm.setOnClickListener(v -> {
                dueIndex[0] = 3;
                applySegGroup(dueButtons, 3);
                refreshDueFields.run();
            });
        }
        refreshDueFields.run();

        final String[] repeatRules;
        if (isAuto) {
            repeatRules = new String[]{"none", "by_km", "by_months", "by_km_or_months"};
            setSpinner(spinnerRepeatRule,
                new String[]{"без повтора", "по пробегу", "по месяцам", "по пробегу или месяцам"});
            spinnerRepeatRule.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                    String rule = repeatRules[pos];
                    inputRepeatKm.setVisibility(
                        ("by_km".equals(rule) || "by_km_or_months".equals(rule)) ? View.VISIBLE : View.GONE);
                    inputRepeatMonths.setVisibility(
                        ("by_months".equals(rule) || "by_km_or_months".equals(rule)) ? View.VISIBLE : View.GONE);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        } else {
            repeatRules = new String[]{"none"};
            spinnerRepeatRule.setVisibility(View.GONE);
            inputRepeatKm.setVisibility(View.GONE);
            inputRepeatMonths.setVisibility(View.GONE);
            View repeatParent = (View) spinnerRepeatRule.getParent();
            if (repeatParent instanceof android.view.ViewGroup) {
                for (int i = 0; i < ((android.view.ViewGroup) repeatParent).getChildCount(); i++) {
                    View child = ((android.view.ViewGroup) repeatParent).getChildAt(i);
                    if (child instanceof TextView
                        && "Повтор".contentEquals(((TextView) child).getText())) {
                        child.setVisibility(View.GONE);
                    }
                }
            }
        }

        new AlertDialog.Builder(this)
            .setTitle("Новая задача")
            .setView(view)
            .setPositiveButton("Добавить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                if (TextUtils.isEmpty(title)) {
                    Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
                    return;
                }
                Task t = new Task(tabId, title, indexToPriority(priorityIndex[0]));
                String desc = inputDescription.getText().toString().trim();
                if (!desc.isEmpty()) t.description = desc;

                t.dueType = dueTypes[dueIndex[0]];
                if ("exact_date".equals(t.dueType)) {
                    t.dueDate = parseDate(inputDueDate.getText().toString());
                } else if ("range".equals(t.dueType)) {
                    t.dueFrom = parseDate(inputDueFrom.getText().toString());
                    t.dueTo = parseDate(inputDueTo.getText().toString());
                } else if ("by_km".equals(t.dueType)) {
                    t.dueKm = parseIntOrNull(inputDueKm.getText().toString());
                }

                if (isAuto) {
                    int repPos = spinnerRepeatRule.getSelectedItemPosition();
                    t.repeatRule = repeatRules[repPos];
                    if ("by_km".equals(t.repeatRule) || "by_km_or_months".equals(t.repeatRule))
                        t.repeatIntervalKm = parseIntOrNull(inputRepeatKm.getText().toString());
                    if ("by_months".equals(t.repeatRule) || "by_km_or_months".equals(t.repeatRule))
                        t.repeatIntervalMonths = parseIntOrNull(inputRepeatMonths.getText().toString());
                } else {
                    t.repeatRule = "none";
                }

                DbExecutor.runVoid(() -> taskDao.insert(t), this::loadTasks);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void setSpinner(Spinner spinner, String[] labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
    }

    private Long parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
            sdf.setLenient(false);
            java.util.Date d = sdf.parse(s.trim());
            return d != null ? d.getTime() : null;
        } catch (java.text.ParseException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showAddShoppingDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_shopping, null);
        EditText inputTitle = view.findViewById(R.id.input_shopping_title);
        EditText inputPrice = view.findViewById(R.id.input_shopping_price);
        EditText inputCategory = view.findViewById(R.id.input_shopping_category);

        if (selectedShoppingCategory != null) {
            inputCategory.setText(selectedShoppingCategory);
        }

        new AlertDialog.Builder(this)
            .setTitle("Новая покупка")
            .setView(view)
            .setPositiveButton("Добавить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                if (TextUtils.isEmpty(title)) {
                    Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
                    return;
                }
                Double price = null;
                String priceStr = inputPrice.getText().toString().trim();
                if (!priceStr.isEmpty()) {
                    try {
                        price = Double.parseDouble(priceStr);
                    } catch (NumberFormatException ignored) {
                    }
                }
                String category = inputCategory.getText().toString().trim();
                final String cat = category.isEmpty() ? null : category;
                final Double priceFinal = price;
                DbExecutor.runVoid(() -> shoppingDao.insert(new ShoppingItem(tabId, title, priceFinal, cat)),
                    this::loadShopping);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void showAddReferenceDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_reference, null);
        EditText inputTitle = view.findViewById(R.id.input_ref_title);
        EditText inputValue = view.findViewById(R.id.input_ref_value);
        EditText inputDescription = view.findViewById(R.id.input_ref_description);
        EditText inputCategory = view.findViewById(R.id.input_ref_category);

        if (selectedReferenceCategory != null) {
            inputCategory.setText(selectedReferenceCategory);
        }

        new AlertDialog.Builder(this)
            .setTitle("Новая запись справочника")
            .setView(view)
            .setPositiveButton("Добавить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                String value = inputValue.getText().toString().trim();
                String description = inputDescription.getText().toString().trim();
                String category = inputCategory.getText().toString().trim();
                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(value)) {
                    Toast.makeText(this, "Название и значение обязательны", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String cat = category.isEmpty() ? null : category;
                final String desc = description.isEmpty() ? null : description;
                DbExecutor.runVoid(() -> referenceDao.insert(
                        new ReferenceEntry(tabId, title, value, cat, desc)),
                    this::loadReference);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private record TasksResult(List<HistoryEntry> entries, List<Task> tasks, Tab tab) {
    }

    private record ShoppingResult(List<String> categories, List<ShoppingItem> items, double total) {
    }

    private record ReferenceResult(List<String> categories, List<ReferenceEntry> items) {
    }
}
