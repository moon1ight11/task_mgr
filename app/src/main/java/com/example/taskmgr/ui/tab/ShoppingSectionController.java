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
import com.example.taskmgr.data.dao.ShoppingDao;
import com.example.taskmgr.model.ShoppingItem;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

class ShoppingSectionController {

    private final Activity activity;
    private final long tabId;
    private final ShoppingDao shoppingDao;

    private final RecyclerView shoppingRecycler;
    private final TextView shoppingEmpty;
    private final TextView totalPrice;
    private final ChipGroup shoppingChipGroup;
    private final MaterialButton btnSortCreated;
    private final MaterialButton btnSortPrice;
    private final MaterialButton btnSortTitle;
    private final ShoppingAdapter shoppingAdapter;

    private String selectedCategory = null;
    private ShoppingDao.SortMode sortMode = ShoppingDao.SortMode.CREATED;
    private boolean sortAsc = false;
    private boolean loading = false;

    ShoppingSectionController(Activity activity, View root, long tabId, ShoppingDao dao) {
        this.activity = activity;
        this.tabId = tabId;
        this.shoppingDao = dao;

        shoppingRecycler = root.findViewById(R.id.shopping_recycler);
        shoppingEmpty = root.findViewById(R.id.shopping_empty);
        totalPrice = root.findViewById(R.id.total_price);
        shoppingChipGroup = root.findViewById(R.id.shopping_chip_group);
        btnSortCreated = root.findViewById(R.id.btn_shop_sort_created);
        btnSortPrice = root.findViewById(R.id.btn_shop_sort_price);
        btnSortTitle = root.findViewById(R.id.btn_shop_sort_title);

        shoppingRecycler.setLayoutManager(new LinearLayoutManager(activity));
        shoppingAdapter = new ShoppingAdapter(
            (item, bought) -> DbExecutor.runVoid(
                () -> shoppingDao.setBought(item.id, bought), this::load),
            this::showActionsDialog
        );
        shoppingRecycler.setAdapter(shoppingAdapter);

        shoppingChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
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

        btnSortCreated.setOnClickListener(v -> toggleSort(ShoppingDao.SortMode.CREATED));
        btnSortPrice.setOnClickListener(v -> toggleSort(ShoppingDao.SortMode.PRICE));
        btnSortTitle.setOnClickListener(v -> toggleSort(ShoppingDao.SortMode.TITLE));

        updateSortButtons();
        load();
    }

    void showAddDialog() {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_add_shopping, null);
        EditText inputTitle = view.findViewById(R.id.input_shopping_title);
        EditText inputPrice = view.findViewById(R.id.input_shopping_price);
        EditText inputCategory = view.findViewById(R.id.input_shopping_category);

        if (selectedCategory != null) inputCategory.setText(selectedCategory);

        new AlertDialog.Builder(activity)
            .setTitle("Новая покупка")
            .setView(view)
            .setPositiveButton("Добавить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                if (TextUtils.isEmpty(title)) {
                    Toast.makeText(activity, "Введите название", Toast.LENGTH_SHORT).show();
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
                DbExecutor.runVoid(
                    () -> shoppingDao.insert(new ShoppingItem(tabId, title, priceFinal, cat)),
                    this::load);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    void load() {
        if (loading) return;
        loading = true;
        final String categoryFilter = selectedCategory;
        final ShoppingDao.SortMode mode = sortMode;
        final boolean asc = sortAsc;

        DbExecutor.run(() -> {
            List<String> categories = shoppingDao.getCategories(tabId);
            List<ShoppingItem> items = shoppingDao.getByTabSorted(tabId, categoryFilter, mode, asc);
            double total = shoppingDao.getTotalPrice(tabId, categoryFilter);
            return new ShoppingResult(categories, items, total);
        }, result -> {
            loading = false;
            if (result == null) return;

            rebuildChips(result.categories());

            shoppingAdapter.setItems(result.items());
            boolean empty = result.items().isEmpty();
            shoppingEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            shoppingRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            totalPrice.setText(String.format(Locale.getDefault(), "Итого: %.2f", result.total()));
        });
    }

    private void toggleSort(ShoppingDao.SortMode mode) {
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
        SegmentedButtons.apply(activity, btnSortCreated,
            sortMode == ShoppingDao.SortMode.CREATED);
        SegmentedButtons.apply(activity, btnSortPrice,
            sortMode == ShoppingDao.SortMode.PRICE);
        SegmentedButtons.apply(activity, btnSortTitle,
            sortMode == ShoppingDao.SortMode.TITLE);

        if (sortMode == ShoppingDao.SortMode.CREATED) {
            SegmentedButtons.setSortIcon(btnSortCreated, sortAsc);
        } else {
            btnSortCreated.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
        if (sortMode == ShoppingDao.SortMode.PRICE) {
            SegmentedButtons.setSortIcon(btnSortPrice, sortAsc);
        } else {
            btnSortPrice.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
        if (sortMode == ShoppingDao.SortMode.TITLE) {
            SegmentedButtons.setSortIcon(btnSortTitle, sortAsc);
        } else {
            btnSortTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    private void rebuildChips(List<String> categories) {
        shoppingChipGroup.setOnCheckedStateChangeListener(null);
        shoppingChipGroup.removeAllViews();

        if (selectedCategory != null && !categories.contains(selectedCategory)) {
            selectedCategory = null;
        }

        Chip allChip = buildChip("Все", null);
        allChip.setChecked(selectedCategory == null);
        shoppingChipGroup.addView(allChip);

        for (String category : categories) {
            Chip chip = buildChip(category, category);
            chip.setChecked(Objects.equals(selectedCategory, category));
            shoppingChipGroup.addView(chip);
        }

        shoppingChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
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

    private void showActionsDialog(ShoppingItem item) {
        new AlertDialog.Builder(activity)
            .setTitle(item.title)
            .setItems(new String[]{"Редактировать", "Удалить"}, (d, which) -> {
                if (which == 0) showEditDialog(item);
                else DbExecutor.runVoid(() -> shoppingDao.delete(item.id), this::load);
            })
            .show();
    }

    private void showEditDialog(ShoppingItem item) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_add_shopping, null);
        EditText inputTitle = view.findViewById(R.id.input_shopping_title);
        EditText inputPrice = view.findViewById(R.id.input_shopping_price);
        EditText inputCategory = view.findViewById(R.id.input_shopping_category);
        inputTitle.setText(item.title);
        if (item.price != null) inputPrice.setText(String.valueOf(item.price));
        if (item.category != null) inputCategory.setText(item.category);

        new AlertDialog.Builder(activity)
            .setTitle("Редактировать покупку")
            .setView(view)
            .setPositiveButton("Сохранить", (d, w) -> {
                String title = inputTitle.getText().toString().trim();
                if (TextUtils.isEmpty(title)) {
                    Toast.makeText(activity, "Введите название", Toast.LENGTH_SHORT).show();
                    return;
                }
                item.title = title;
                String priceStr = inputPrice.getText().toString().trim();
                item.price = priceStr.isEmpty() ? null : Double.parseDouble(priceStr);
                String category = inputCategory.getText().toString().trim();
                item.category = category.isEmpty() ? null : category;
                DbExecutor.runVoid(() -> shoppingDao.update(item), this::load);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private record ShoppingResult(List<String> categories, List<ShoppingItem> items, double total) {
    }
}
