package com.example.taskmgr.ui.tab;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.ReferenceDao;
import com.example.taskmgr.model.ReferenceEntry;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.Objects;

class ReferenceSectionController {

    private final Activity activity;
    private final long tabId;
    private final ReferenceDao referenceDao;

    private final RecyclerView referenceRecycler;
    private final TextView referenceEmpty;
    private final ChipGroup referenceChipGroup;
    private final MaterialButton btnSortTitle;
    private final MaterialButton btnSortCategory;
    private final ReferenceAdapter referenceAdapter;

    private String selectedCategory = null;
    private ReferenceDao.SortMode sortMode = ReferenceDao.SortMode.TITLE;
    private boolean sortAsc = false;
    private boolean loading = false;

    ReferenceSectionController(Activity activity, View root, long tabId, ReferenceDao dao) {
        this.activity = activity;
        this.tabId = tabId;
        this.referenceDao = dao;

        referenceRecycler = root.findViewById(R.id.reference_recycler);
        referenceEmpty = root.findViewById(R.id.reference_empty);
        referenceChipGroup = root.findViewById(R.id.reference_chip_group);
        btnSortTitle = root.findViewById(R.id.btn_ref_sort_title);
        btnSortCategory = root.findViewById(R.id.btn_ref_sort_category);

        referenceRecycler.setLayoutManager(new LinearLayoutManager(activity));
        referenceAdapter = new ReferenceAdapter(this::showActionsDialog);
        referenceRecycler.setAdapter(referenceAdapter);

        referenceChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                if (selectedCategory != null) {
                    selectedCategory = null;
                    load();
                }
                return;
            }
            Chip chip = group.findViewById(checkedIds.get(0));
            String newCategory = (chip != null && chip.getTag() != null)
                ? chip.getTag().toString()
                : null;
            if (!Objects.equals(selectedCategory, newCategory)) {
                selectedCategory = newCategory;
                load();
            }
        });

        btnSortTitle.setOnClickListener(v -> toggleSort(ReferenceDao.SortMode.TITLE));
        btnSortCategory.setOnClickListener(v -> toggleSort(ReferenceDao.SortMode.CATEGORY));

        updateSortButtons();
        load();
    }

    void showAddDialog() {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_add_reference, null);
        EditText inputTitle = view.findViewById(R.id.input_ref_title);
        EditText inputValue = view.findViewById(R.id.input_ref_value);
        EditText inputDescription = view.findViewById(R.id.input_ref_description);
        EditText inputCategory = view.findViewById(R.id.input_ref_category);

        if (selectedCategory != null) inputCategory.setText(selectedCategory);

        new AlertDialog.Builder(activity)
            .setTitle("Новая запись справочника")
            .setView(view)
            .setPositiveButton("Добавить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                String value = inputValue.getText().toString().trim();
                String description = inputDescription.getText().toString().trim();
                String category = inputCategory.getText().toString().trim();
                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(value)) {
                    Toast.makeText(activity, "Название и значение обязательны",
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                final String cat = category.isEmpty() ? null : category;
                final String desc = description.isEmpty() ? null : description;
                DbExecutor.runVoid(
                    () -> referenceDao.insert(new ReferenceEntry(tabId, title, value, cat, desc)),
                    this::load);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    void load() {
        if (loading) return;
        loading = true;
        final String categoryFilter = selectedCategory;
        final ReferenceDao.SortMode mode = sortMode;
        final boolean asc = sortAsc;

        DbExecutor.run(() -> {
            List<String> categories = referenceDao.getCategories(tabId);
            List<ReferenceEntry> items = referenceDao.getByTabSorted(tabId, categoryFilter, mode, asc);
            return new ReferenceResult(categories, items);
        }, result -> {
            loading = false;
            if (result == null) return;

            rebuildChips(result.categories());

            referenceAdapter.setItems(result.items());
            boolean empty = result.items().isEmpty();
            referenceEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            referenceRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private void toggleSort(ReferenceDao.SortMode mode) {
        if (sortMode == mode) {
            sortAsc = !sortAsc;
        } else {
            sortMode = mode;
            sortAsc = false;
        }
        updateSortButtons();
        load();
    }

    private void updateSortButtons() {
        SegmentedButtons.apply(activity, btnSortTitle,
            sortMode == ReferenceDao.SortMode.TITLE);
        SegmentedButtons.apply(activity, btnSortCategory,
            sortMode == ReferenceDao.SortMode.CATEGORY);

        if (sortMode == ReferenceDao.SortMode.TITLE) {
            SegmentedButtons.setSortIcon(btnSortTitle, sortAsc);
        } else {
            btnSortTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
        if (sortMode == ReferenceDao.SortMode.CATEGORY) {
            SegmentedButtons.setSortIcon(btnSortCategory, sortAsc);
        } else {
            btnSortCategory.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    private void rebuildChips(List<String> categories) {
        referenceChipGroup.setOnCheckedStateChangeListener(null);
        referenceChipGroup.removeAllViews();

        if (selectedCategory != null && !categories.contains(selectedCategory)) {
            selectedCategory = null;
        }

        Chip allChip = buildChip("Все", null);
        allChip.setChecked(selectedCategory == null);
        referenceChipGroup.addView(allChip);

        for (String category : categories) {
            Chip chip = buildChip(category, category);
            chip.setChecked(Objects.equals(selectedCategory, category));
            referenceChipGroup.addView(chip);
        }

        referenceChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                if (selectedCategory != null) {
                    selectedCategory = null;
                    load();
                }
                return;
            }
            Chip chip = group.findViewById(checkedIds.get(0));
            String newCategory = (chip != null && chip.getTag() != null)
                ? chip.getTag().toString()
                : null;
            if (!Objects.equals(selectedCategory, newCategory)) {
                selectedCategory = newCategory;
                load();
            }
        });
    }

    private Chip buildChip(String label, String tag) {
        Chip chip = new Chip(activity);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setTag(tag);
        return chip;
    }

    private void showActionsDialog(ReferenceEntry item) {
        new AlertDialog.Builder(activity)
            .setTitle(item.title)
            .setItems(new String[]{"Редактировать", "Удалить"}, (d, which) -> {
                if (which == 0) showEditDialog(item);
                else DbExecutor.runVoid(() -> referenceDao.delete(item.id), this::load);
            })
            .show();
    }

    private void showEditDialog(ReferenceEntry item) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_add_reference, null);
        EditText inputTitle = view.findViewById(R.id.input_ref_title);
        EditText inputValue = view.findViewById(R.id.input_ref_value);
        EditText inputDescription = view.findViewById(R.id.input_ref_description);
        EditText inputCategory = view.findViewById(R.id.input_ref_category);
        inputTitle.setText(item.title);
        inputValue.setText(item.value);
        if (item.description != null) inputDescription.setText(item.description);
        if (item.category != null) inputCategory.setText(item.category);

        new AlertDialog.Builder(activity)
            .setTitle("Редактировать запись")
            .setView(view)
            .setPositiveButton("Сохранить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                String value = inputValue.getText().toString().trim();
                String description = inputDescription.getText().toString().trim();
                String category = inputCategory.getText().toString().trim();
                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(value)) {
                    Toast.makeText(activity, "Название и значение обязательны",
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                item.title = title;
                item.value = value;
                item.description = description.isEmpty() ? null : description;
                item.category = category.isEmpty() ? null : category;
                DbExecutor.runVoid(() -> referenceDao.update(item), this::load);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private record ReferenceResult(List<String> categories, List<ReferenceEntry> items) {
    }
}
