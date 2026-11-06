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
import com.example.taskmgr.service.TaskUrgencyComparator;
import com.example.taskmgr.util.AppLock;
import com.example.taskmgr.ui.common.CompleteTaskDialog;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TabActivity extends AppCompatActivity {

    public static final String EXTRA_TAB_ID = "tab_id";
    public static final String EXTRA_TAB_NAME = "tab_name";
    public static final String EXTRA_IS_AUTO = "is_auto";
    public static final String EXTRA_TASK_ID = "task_id";

    private long tabId;
    private String tabName;
    private boolean isAuto;
    private long extraTaskId = -1;

    private TabDao tabDao;
    private TaskDao taskDao;
    private HistoryDao historyDao;
    private ShoppingDao shoppingDao;
    private ReferenceDao referenceDao;

    private TabLayout tabTabs;
    private FrameLayout contentContainer;
    private FloatingActionButton fabAdd;

    private View viewTasks, viewShopping, viewReference;

    private RecyclerView tasksRecycler;
    private TextView tasksEmpty;
    private TaskFullAdapter taskAdapter;
    private HistoryAdapter historyAdapter;
    private MaterialButton btnActive, btnHistory, btnSortCreated, btnSortDeadline;
    private boolean showingHistory = false;
    private boolean sortByDeadline = false;
    private int loadGeneration = 0;

    private RecyclerView shoppingRecycler;
    private TextView shoppingEmpty, totalPrice;
    private ShoppingAdapter shoppingAdapter;
    private boolean loadingShopping = false;
    private String selectedShoppingCategory = null;
    private ChipGroup shoppingChipGroup;

    private RecyclerView referenceRecycler;
    private TextView referenceEmpty;
    private ReferenceAdapter referenceAdapter;
    private boolean loadingReference = false;

    private String selectedReferenceCategory = null;
    private ChipGroup referenceChipGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab);
        tabDao = new TabDao(this);

        tabId = getIntent().getLongExtra(EXTRA_TAB_ID, -1);
        tabName = getIntent().getStringExtra(EXTRA_TAB_NAME);
        isAuto = getIntent().getBooleanExtra(EXTRA_IS_AUTO, false);
        extraTaskId = getIntent().getLongExtra(EXTRA_TASK_ID, -1);

        Tab t = tabDao.getById(tabId);
        if (t == null) {
            finish();
            return;
        }
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

        tabTabs.addTab(tabTabs.newTab().setText("Задачи"));
        tabTabs.addTab(tabTabs.newTab().setText("Покупки"));
        tabTabs.addTab(tabTabs.newTab().setText("Справочник"));
        tabTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        showTab(0);
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
        btnActive = viewTasks.findViewById(R.id.btn_active);
        btnHistory = viewTasks.findViewById(R.id.btn_history);
        btnSortCreated = viewTasks.findViewById(R.id.btn_sort_created);
        btnSortDeadline = viewTasks.findViewById(R.id.btn_sort_deadline);

        tasksRecycler.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TaskFullAdapter(this::showTaskActionsDialog, this::showTaskActionsDialog);
        historyAdapter = new HistoryAdapter();
        tasksRecycler.setAdapter(taskAdapter);

        btnActive.setOnClickListener(v -> setTaskMode(false));
        btnHistory.setOnClickListener(v -> setTaskMode(true));
        btnSortCreated.setOnClickListener(v -> setTaskSort(false));
        btnSortDeadline.setOnClickListener(v -> setTaskSort(true));

        updateTaskButtons();
        loadTasks();
    }

    private void setTaskMode(boolean history) {
        if (showingHistory == history) return;
        showingHistory = history;
        updateTaskButtons();
        loadTasks();
    }

    private void setTaskSort(boolean byDeadline) {
        if (showingHistory) return;
        if (sortByDeadline == byDeadline) return;
        sortByDeadline = byDeadline;
        updateTaskButtons();
        loadTasks();
    }

    private void updateTaskButtons() {
        int accent = ContextCompat.getColor(this, R.color.neon_green);
        int accentDim = ContextCompat.getColor(this, R.color.neon_green_dim);
        int inactiveStroke = ContextCompat.getColor(this, R.color.divider_strong);
        int inactiveText = ContextCompat.getColor(this, R.color.text_secondary);
        int disabledText = ContextCompat.getColor(this, R.color.text_tertiary);

        applySeg(btnActive, !showingHistory, accent, accentDim, inactiveStroke,
                inactiveText, disabledText);
        applySeg(btnHistory, showingHistory, accent, accentDim, inactiveStroke,
                inactiveText, disabledText);

        boolean sortEnabled = !showingHistory;
        applySeg(btnSortCreated, sortEnabled && !sortByDeadline, accent, accentDim,
                inactiveStroke, sortEnabled ? inactiveText : disabledText, disabledText);
        applySeg(btnSortDeadline, sortEnabled && sortByDeadline, accent, accentDim,
                inactiveStroke, sortEnabled ? inactiveText : disabledText, disabledText);

        btnSortCreated.setEnabled(sortEnabled);
        btnSortDeadline.setEnabled(sortEnabled);
    }

    private void applySeg(MaterialButton b, boolean checked,
                          int accent, int accentDim, int inactiveStroke,
                          int inactiveText, int disabledText) {
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
        final long pendingTaskId = extraTaskId;
        DbExecutor.run(() -> {
            if (history) {
                List<Task> allTasks = taskDao.getAllByTab(tabId);
                List<HistoryEntry> entries = new ArrayList<>();
                for (Task t : allTasks) entries.addAll(historyDao.getByTask(t.id));
                entries.sort((a, b) -> Long.compare(b.completedAt, a.completedAt));
                return new Object[]{entries, allTasks};
            } else {
                List<Task> activeTasks = taskDao.getByTab(tabId);
                Tab currentTab = tabDao.getById(tabId);

                if (sortDeadline && currentTab != null) {
                    Map<Long, Tab> tabsById = new HashMap<>();
                    tabsById.put(currentTab.id, currentTab);
                    Collections.sort(activeTasks, new TaskUrgencyComparator(tabsById));
                }
                return new Object[]{activeTasks, currentTab};
            }
        }, result -> {
            if (myGeneration != loadGeneration) return;
            if (result == null) return;
            Object[] arr = (Object[]) result;
            if (history) {
                @SuppressWarnings("unchecked")
                List<HistoryEntry> entries = (List<HistoryEntry>) arr[0];
                @SuppressWarnings("unchecked")
                List<Task> allTasks = (List<Task>) arr[1];
                tasksRecycler.setAdapter(historyAdapter);
                historyAdapter.setItems(entries, allTasks);
                boolean empty = entries.isEmpty();
                tasksEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                tasksRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            } else {
                @SuppressWarnings("unchecked")
                List<Task> tasks = (List<Task>) arr[0];
                Tab currentTab = (Tab) arr[1];
                tasksRecycler.setAdapter(taskAdapter);
                taskAdapter.setCurrentTab(currentTab);
                taskAdapter.setItems(tasks);
                boolean empty = tasks.isEmpty();
                tasksEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                tasksRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);

                if (pendingTaskId > 0) {
                    extraTaskId = -1;
                    final long id = pendingTaskId;
                    final int pos = taskAdapter.positionOfTask(id);
                    if (pos >= 0) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            tasksRecycler.scrollToPosition(pos);
                            taskAdapter.setHighlightedTaskId(id);
                            new Handler(Looper.getMainLooper()).postDelayed(
                                    taskAdapter::clearHighlight, 1200);
                        }, 150);
                    }
                }
            }
        });
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
        Spinner spinnerPriority = view.findViewById(R.id.spinner_priority);
        Spinner spinnerDueType = view.findViewById(R.id.spinner_due_type);
        Spinner spinnerRepeatRule = view.findViewById(R.id.spinner_repeat_rule);

        EditText inputDueDate = view.findViewById(R.id.input_due_date);
        EditText inputDueFrom = view.findViewById(R.id.input_due_from);
        EditText inputDueTo = view.findViewById(R.id.input_due_to);
        EditText inputDueKm = view.findViewById(R.id.input_due_km);
        EditText inputRepeatKm = view.findViewById(R.id.input_repeat_km);
        EditText inputRepeatMonths = view.findViewById(R.id.input_repeat_months);

        pickDate(inputDueDate);
        pickDate(inputDueFrom);
        pickDate(inputDueTo);

        final String[] dueTypes = isAuto
                ? new String[]{"none", "exact_date", "range", "by_km"}
                : new String[]{"none", "exact_date", "range"};
        final String[] dueLabels = isAuto
                ? new String[]{"без срока", "точная дата", "период", "по пробегу"}
                : new String[]{"без срока", "точная дата", "период"};

        final String[] priorities = {"important", "normal", "whatever"};
        final String[] repeatRules;
        if (isAuto) {
            repeatRules = new String[]{"none", "by_km", "by_months", "by_km_or_months"};
        } else {
            repeatRules = new String[]{"none"};
        }

        setSpinner(spinnerPriority, new String[]{"важно", "обычно", "потом"}, 1);
        setSpinner(spinnerDueType, dueLabels, 0);

        if (isAuto) {
            setSpinner(spinnerRepeatRule,
                    new String[]{"без повтора", "по пробегу", "по месяцам", "по пробегу или месяцам"}, 0);
        } else {
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

        spinnerDueType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                String type = dueTypes[pos];
                inputDueDate.setVisibility("exact_date".equals(type) ? View.VISIBLE : View.GONE);
                inputDueFrom.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
                inputDueTo.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
                inputDueKm.setVisibility("by_km".equals(type) ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        if (isAuto) {
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
        }

        inputTitle.setText(task.title);
        if (task.description != null) inputDescription.setText(task.description);
        for (int i = 0; i < priorities.length; i++) {
            if (priorities[i].equals(task.priority)) {
                spinnerPriority.setSelection(i);
                break;
            }
        }

        int dueIdx = 0;
        for (int i = 0; i < dueTypes.length; i++) {
            if (dueTypes[i].equals(task.dueType)) {
                dueIdx = i;
                break;
            }
        }
        spinnerDueType.setSelection(dueIdx);
        if ("exact_date".equals(task.dueType) && task.dueDate != null) {
            inputDueDate.setText(formatDateForInput(task.dueDate));
        } else if ("range".equals(task.dueType)) {
            if (task.dueFrom != null) inputDueFrom.setText(formatDateForInput(task.dueFrom));
            if (task.dueTo != null) inputDueTo.setText(formatDateForInput(task.dueTo));
        } else if ("by_km".equals(task.dueType) && task.dueKm != null) {
            inputDueKm.setText(String.valueOf(task.dueKm));
        }

        if (isAuto && task.repeatRule != null) {
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
                    task.priority = priorities[spinnerPriority.getSelectedItemPosition()];
                    String desc = inputDescription.getText().toString().trim();
                    task.description = desc.isEmpty() ? null : desc;

                    int duePos = spinnerDueType.getSelectedItemPosition();
                    task.dueType = dueTypes[duePos];
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

        shoppingRecycler.setLayoutManager(new LinearLayoutManager(this));
        shoppingAdapter = new ShoppingAdapter(
                (item, bought) -> {
                    DbExecutor.run(() -> shoppingDao.setBought(item.id, bought));
                    updateTotalPrice();
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
            if (!equalsNullable(selectedShoppingCategory, newCategory)) {
                selectedShoppingCategory = newCategory;
                loadShopping();
            }
        });

        loadShopping();
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
        DbExecutor.run(() -> {
            List<String> categories = shoppingDao.getCategories(tabId);
            List<ShoppingItem> items = shoppingDao.getByTabAndCategory(tabId, categoryFilter);
            Double total = shoppingDao.getTotalPrice(tabId, categoryFilter);
            return new Object[]{categories, items, total};
        }, result -> {
            loadingShopping = false;
            if (result == null) return;
            Object[] arr = (Object[]) result;
            @SuppressWarnings("unchecked")
            List<String> categories = (List<String>) arr[0];
            @SuppressWarnings("unchecked")
            List<ShoppingItem> items = (List<ShoppingItem>) arr[1];
            Double total = (Double) arr[2];

            rebuildShoppingChips(categories);

            shoppingAdapter.setItems(items);
            boolean empty = items.isEmpty();
            shoppingEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            shoppingRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            totalPrice.setText(String.format(Locale.getDefault(),"Итого: %.2f", total));
        });
    }

    private void updateTotalPrice() {
        final String categoryFilter = selectedShoppingCategory;
        DbExecutor.run(() -> shoppingDao.getTotalPrice(tabId, categoryFilter),
                total -> totalPrice.setText(String.format(Locale.getDefault(),"Итого: %.2f", total)));
    }

    private void setupReferenceView() {
        referenceRecycler = viewReference.findViewById(R.id.reference_recycler);
        referenceEmpty = viewReference.findViewById(R.id.reference_empty);
        referenceChipGroup = viewReference.findViewById(R.id.reference_chip_group);

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
            if (!equalsNullable(selectedReferenceCategory, newCategory)) {
                selectedReferenceCategory = newCategory;
                loadReference();
            }
        });

        loadReference();
    }

    private static boolean equalsNullable(String a, String b) {
        return a == null ? b == null : a.equals(b);
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
            c.setChecked(equalsNullable(selectedShoppingCategory, cat));
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
            if (!equalsNullable(selectedShoppingCategory, newCategory)) {
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
            c.setChecked(equalsNullable(selectedReferenceCategory, cat));
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
            if (!equalsNullable(selectedReferenceCategory, newCategory)) {
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
        EditText inputCategory = view.findViewById(R.id.input_ref_category);
        inputTitle.setText(item.title);
        inputValue.setText(item.value);
        if (item.category != null) inputCategory.setText(item.category);

        new AlertDialog.Builder(this)
                .setTitle("Редактировать запись")
                .setView(view)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String title = inputTitle.getText().toString().trim();
                    String value = inputValue.getText().toString().trim();
                    String category = inputCategory.getText().toString().trim();
                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(value)) {
                        Toast.makeText(this, "Название и значение обязательны", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    item.title = title;
                    item.value = value;
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
        DbExecutor.run(() -> {
            List<String> categories = referenceDao.getCategories(tabId);
            List<ReferenceEntry> items = referenceDao.getByTabAndCategory(tabId, categoryFilter);
            return new Object[]{categories, items};
        }, result -> {
            loadingReference = false;
            if (result == null) return;
            Object[] arr = (Object[]) result;
            @SuppressWarnings("unchecked")
            List<String> categories = (List<String>) arr[0];
            @SuppressWarnings("unchecked")
            List<ReferenceEntry> items = (List<ReferenceEntry>) arr[1];

            rebuildReferenceChips(categories);

            referenceAdapter.setItems(items);
            boolean empty = items.isEmpty();
            referenceEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            referenceRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private void showTab(int position) {
        viewTasks.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        viewShopping.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        viewReference.setVisibility(position == 2 ? View.VISIBLE : View.GONE);

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

    private void showAddTaskDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        EditText inputTitle = view.findViewById(R.id.input_title);
        EditText inputDescription = view.findViewById(R.id.input_description);
        Spinner spinnerPriority = view.findViewById(R.id.spinner_priority);
        Spinner spinnerDueType = view.findViewById(R.id.spinner_due_type);
        Spinner spinnerRepeatRule = view.findViewById(R.id.spinner_repeat_rule);
        EditText inputDueDate = view.findViewById(R.id.input_due_date);
        EditText inputDueFrom = view.findViewById(R.id.input_due_from);
        EditText inputDueTo = view.findViewById(R.id.input_due_to);
        EditText inputDueKm = view.findViewById(R.id.input_due_km);
        EditText inputRepeatKm = view.findViewById(R.id.input_repeat_km);
        EditText inputRepeatMonths = view.findViewById(R.id.input_repeat_months);

        pickDate(inputDueDate);
        pickDate(inputDueFrom);
        pickDate(inputDueTo);

        final String[] dueTypes = isAuto
                ? new String[]{"none", "exact_date", "range", "by_km"}
                : new String[]{"none", "exact_date", "range"};
        final String[] dueLabels = isAuto
                ? new String[]{"без срока", "точная дата", "период", "по пробегу"}
                : new String[]{"без срока", "точная дата", "период"};

        final String[] priorities = {"important", "normal", "whatever"};
        setSpinner(spinnerPriority, new String[]{"важно", "обычно", "потом"}, 1);
        setSpinner(spinnerDueType, dueLabels, 0);

        final String[] repeatRules;
        if (isAuto) {
            repeatRules = new String[]{"none", "by_km", "by_months", "by_km_or_months"};
            setSpinner(spinnerRepeatRule,
                    new String[]{"без повтора", "по пробегу", "по месяцам", "по пробегу или месяцам"}, 0);
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

        spinnerDueType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                String type = dueTypes[pos];
                inputDueDate.setVisibility("exact_date".equals(type) ? View.VISIBLE : View.GONE);
                inputDueFrom.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
                inputDueTo.setVisibility("range".equals(type) ? View.VISIBLE : View.GONE);
                inputDueKm.setVisibility("by_km".equals(type) ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        if (isAuto) {
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
                    Task t = new Task(tabId, title, priorities[spinnerPriority.getSelectedItemPosition()]);
                    String desc = inputDescription.getText().toString().trim();
                    if (!desc.isEmpty()) t.description = desc;

                    int duePos = spinnerDueType.getSelectedItemPosition();
                    t.dueType = dueTypes[duePos];
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

    private void setSpinner(Spinner spinner, String[] labels, int defaultSelection) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(defaultSelection);
    }

    private Long parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
            sdf.setLenient(false);
            return sdf.parse(s.trim()).getTime();
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
                    String category = inputCategory.getText().toString().trim();
                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(value)) {
                        Toast.makeText(this, "Название и значение обязательны", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    final String cat = category.isEmpty() ? null : category;
                    DbExecutor.runVoid(() -> referenceDao.insert(new ReferenceEntry(tabId, title, value, cat)),
                            this::loadReference);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}