package com.example.taskmgr.ui.finance;

import android.app.Activity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.FinanceExpenseDao;
import com.example.taskmgr.data.dao.FinanceIncomeDao;
import com.example.taskmgr.data.dao.FinancePeriodDao;
import com.example.taskmgr.data.dao.FinanceTemplateDao;
import com.example.taskmgr.data.dao.SectionDao;
import com.example.taskmgr.data.dao.ShoppingDao;
import com.example.taskmgr.data.dao.SubscriptionDao;
import com.example.taskmgr.data.dao.TabDao;
import com.example.taskmgr.model.FinanceExpense;
import com.example.taskmgr.model.FinanceIncome;
import com.example.taskmgr.model.FinancePeriod;
import com.example.taskmgr.model.FinanceTemplate;
import com.example.taskmgr.model.Section;
import com.example.taskmgr.model.ShoppingItem;
import com.example.taskmgr.model.Subscription;
import com.example.taskmgr.model.Tab;
import com.example.taskmgr.util.DbExecutor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FinancePeriodsController {

    private final Activity activity;
    private final View root;
    private final FinanceIncomeDao incomeDao;
    private final FinancePeriodDao periodDao;
    private final FinanceExpenseDao expenseDao;
    private final FinanceTemplateDao templateDao;
    private final List<FinanceTemplate> templatesForPick = new ArrayList<>();
    private final Set<Long> pickedTemplateIds = new HashSet<>();
    private final Set<Long> pickedSubIds = new HashSet<>();
    private List<FinancePeriod> periods = new ArrayList<>();
    private int currentIndex = 0;
    private FinancePeriod currentPeriod;

    public FinancePeriodsController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        incomeDao = new FinanceIncomeDao(activity);
        periodDao = new FinancePeriodDao(activity);
        expenseDao = new FinanceExpenseDao(activity);
        templateDao = new FinanceTemplateDao(activity);

        root.findViewById(R.id.btn_prev_period).setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                bindCurrentPeriod();
            }
        });
        root.findViewById(R.id.btn_next_period).setOnClickListener(v -> {
            if (currentIndex < periods.size() - 1) {
                currentIndex++;
                bindCurrentPeriod();
            }
        });
        root.findViewById(R.id.btn_fin_add_expense).setOnClickListener(v -> showAddExpenseDialog());
        root.findViewById(R.id.btn_fin_add_from_template)
            .setOnClickListener(v -> showPickTemplatesDialog());
        root.findViewById(R.id.btn_fin_add_subs)
            .setOnClickListener(v -> showPickSubscriptionsDialog());
        root.findViewById(R.id.btn_fin_add_shopping)
            .setOnClickListener(v -> showPickShoppingDialog());
    }

    public void load() {
        DbExecutor.run(() -> {
            ensurePeriodsGenerated();
            List<FinancePeriod> list = periodDao.getAll();
            int idx = 0;
            long now = System.currentTimeMillis();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).startAt <= now && list.get(i).endAt > now) {
                    idx = i;
                    break;
                }
            }
            return new LoadResult(list, idx);
        }, res -> {
            if (res == null) return;
            periods = res.periods;
            currentIndex = res.index;
            bindCurrentPeriod();
        });
    }

    public void showPickTemplatesDialog() {
        if (currentPeriod == null) {
            Toast.makeText(activity, "Сначала выберите период", Toast.LENGTH_SHORT).show();
            return;
        }
        DbExecutor.run(
            () -> templateDao.getAll(true),
            (List<FinanceTemplate> list) -> {
                if (list == null || list.isEmpty()) {
                    Toast.makeText(activity, "Шаблонов нет. Добавьте их в «Настройках»",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                templatesForPick.clear();
                templatesForPick.addAll(list);
                pickedTemplateIds.clear();

                View v = activity.getLayoutInflater()
                    .inflate(R.layout.dialog_finance_templates_pick, null);
                LinearLayout container = v.findViewById(R.id.container_template_pick);

                for (FinanceTemplate t : templatesForPick) {
                    CheckBox cb = new CheckBox(activity);
                    String catPart = t.category;
                    String subPart = (t.title != null && !t.title.isEmpty()) ? " · " + t.title : "";
                    String amountPart = String.format(Locale.getDefault(), " — %.2f ₽", t.defaultAmount);
                    String label = catPart + subPart + amountPart;
                    cb.setText(label);
                    cb.setTextSize(14);
                    cb.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
                    cb.setPadding(0, dp(6), 0, dp(6));
                    long id = t.id;
                    cb.setOnCheckedChangeListener((b, checked) -> {
                        if (checked) pickedTemplateIds.add(id);
                        else pickedTemplateIds.remove(id);
                    });
                    container.addView(cb);
                }

                new AlertDialog.Builder(activity)
                    .setTitle("Добавить из шаблона")
                    .setView(v)
                    .setPositiveButton("Добавить", (d, w) -> {
                        if (pickedTemplateIds.isEmpty()) return;
                        List<FinanceTemplate> chosen = new ArrayList<>();
                        for (FinanceTemplate t : templatesForPick) {
                            if (pickedTemplateIds.contains(t.id)) chosen.add(t);
                        }
                        final long pid = currentPeriod.id;
                        DbExecutor.runVoid(() -> {
                            long now = System.currentTimeMillis();
                            for (FinanceTemplate t : chosen) {
                                FinanceExpense e = new FinanceExpense();
                                e.periodId = pid;
                                e.category = t.category;
                                e.title = t.title;
                                e.amount = t.defaultAmount;
                                e.createdAt = now++;
                                e.sourceType = "template";
                                long id = expenseDao.insert(e);
                                if (id <= 0) android.util.Log.e("Finance",
                                    "template insert failed, periodId=" + pid);
                            }
                        }, this::bindCurrentPeriod);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            }
        );
    }

    public void showPickSubscriptionsDialog() {
        if (currentPeriod == null) {
            Toast.makeText(activity, "Сначала выберите период", Toast.LENGTH_SHORT).show();
            return;
        }
        final long pid = currentPeriod.id;
        final long from = currentPeriod.startAt;
        final long to = currentPeriod.endAt;

        DbExecutor.run(() -> {
            SubscriptionDao subDao = new SubscriptionDao(activity);
            List<Subscription> subs = subDao.getUpcomingWithin(from, to - 1);
            Set<Long> already = expenseDao.getSubscriptionBundleRefIds(pid);
            already.addAll(expenseDao.getSubscriptionRefIdsForPeriod(pid));
            return new SubPickResult(subs, already);
        }, res -> {
            if (res == null) return;
            List<Subscription> subs = res.subs;
            Set<Long> already = res.already;
            if (subs == null || subs.isEmpty()) {
                Toast.makeText(activity, "В этом периоде нет списаний подписок",
                    Toast.LENGTH_LONG).show();
                return;
            }

            List<Subscription> available = new ArrayList<>();
            for (Subscription s : subs) if (!already.contains(s.id)) available.add(s);
            if (available.isEmpty()) {
                Toast.makeText(activity, "Все подписки этого периода уже добавлены",
                    Toast.LENGTH_LONG).show();
                return;
            }

            pickedSubIds.clear();

            View v = activity.getLayoutInflater()
                .inflate(R.layout.dialog_finance_subs_pick, null);
            LinearLayout container = v.findViewById(R.id.container_subs_pick);

            SimpleDateFormat df = new SimpleDateFormat("dd.MM", Locale.getDefault());

            for (Subscription s : available) {
                CheckBox cb = new CheckBox(activity);
                String label = s.name + " · " + df.format(new Date(s.nextChargeAt))
                    + " — " + String.format(Locale.getDefault(), "%.2f ₽", s.amount);
                cb.setText(label);
                cb.setTextSize(14);
                cb.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
                cb.setPadding(0, dp(6), 0, dp(6));
                long id = s.id;
                cb.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) pickedSubIds.add(id);
                    else pickedSubIds.remove(id);
                });
                container.addView(cb);
            }

            new AlertDialog.Builder(activity)
                .setTitle("Добавить списания подписок")
                .setView(v)
                .setPositiveButton("Добавить", (d, w) -> {
                    if (pickedSubIds.isEmpty()) return;
                    StringBuilder names = new StringBuilder();
                    StringBuilder refs = new StringBuilder();
                    double total = 0;
                    for (Subscription s : available) {
                        if (!pickedSubIds.contains(s.id)) continue;
                        if (names.length() > 0) names.append(", ");
                        names.append(s.name);
                        if (refs.length() > 0) refs.append(",");
                        refs.append(s.id);
                        total += s.amount;
                    }
                    final double totalFinal = total;
                    final String namesFinal = names.toString();
                    final String refsFinal = refs.toString();

                    DbExecutor.runVoid(() -> {
                        FinanceExpense e = new FinanceExpense();
                        e.periodId = pid;
                        e.category = "Подписки";
                        e.title = namesFinal;
                        e.amount = totalFinal;
                        e.createdAt = System.currentTimeMillis();
                        e.sourceType = "subscription_bundle";
                        e.sourceRefId = null;
                        e.sourceRefs = refsFinal;
                        expenseDao.insert(e);
                    }, this::bindCurrentPeriod);
                })
                .setNegativeButton("Отмена", null)
                .show();
        });
    }

    public void showPickShoppingDialog() {
        if (currentPeriod == null) {
            Toast.makeText(activity, "Сначала выберите период", Toast.LENGTH_SHORT).show();
            return;
        }
        final long pid = currentPeriod.id;

        DbExecutor.run(() -> {
            TabDao tabDao = new TabDao(activity);
            SectionDao sectionDao = new SectionDao(activity);
            ShoppingDao shopDao = new ShoppingDao(activity);

            List<Tab> allTabs = tabDao.getAll();
            Map<Long, String> sectionNames = new HashMap<>();
            Set<Long> privateSectionIds = new HashSet<>();
            for (Section s : sectionDao.getAll()) {
                sectionNames.put(s.id, s.name);
                if (s.isPrivate) privateSectionIds.add(s.id);
            }

            List<TabShoppingInfo> infos = new ArrayList<>();
            for (Tab t : allTabs) {
                if (!t.showShopping) continue;
                if (privateSectionIds.contains(t.sectionId)) continue;
                List<ShoppingItem> items = shopDao.getByTabSorted(
                    t.id, null, ShoppingDao.SortMode.CREATED, true);
                if (items.isEmpty()) continue;
                int notBought = 0;
                double sum = 0;
                for (ShoppingItem it : items) {
                    if (!it.isBought) notBought++;
                    if (it.price != null) sum += it.price;
                }
                String sectionName = sectionNames.get(t.sectionId);
                String label = (sectionName != null && !sectionName.isEmpty())
                    ? sectionName + " • " + t.name
                    : t.name;
                TabShoppingInfo info = new TabShoppingInfo();
                info.tabId = t.id;
                info.tabName = t.name;
                info.label = label;
                info.count = items.size();
                info.notBought = notBought;
                info.sum = sum;
                infos.add(info);
            }
            Set<Long> already = expenseDao.getShoppingBundleRefIds(pid);
            return new ShoppingPickResult(infos, already);
        }, res -> {
            if (res == null) return;
            if (res.infos == null || res.infos.isEmpty()) {
                Toast.makeText(activity, "Нет вкладок с покупками", Toast.LENGTH_LONG).show();
                return;
            }
            String[] labels = new String[res.infos.size()];
            for (int i = 0; i < res.infos.size(); i++) {
                TabShoppingInfo info = res.infos.get(i);
                labels[i] = info.label + " · " + info.notBought + "/" + info.count + " не куплено · "
                    + String.format(Locale.getDefault(), "%.2f ₽", info.sum);
            }
            new AlertDialog.Builder(activity)
                .setTitle("Выберите вкладку")
                .setItems(labels, (d, which) -> {
                    TabShoppingInfo info = res.infos.get(which);
                    showShoppingItemsDialog(info, res.already, pid);
                })
                .setNegativeButton("Отмена", null)
                .show();
        });
    }

    private void showShoppingItemsDialog(TabShoppingInfo info, Set<Long> already, long pid) {
        DbExecutor.run(() -> {
            ShoppingDao shopDao = new ShoppingDao(activity);
            List<ShoppingItem> items = shopDao.getByTabSorted(
                info.tabId, null, ShoppingDao.SortMode.CREATED, true);
            List<ShoppingItem> available = new ArrayList<>();
            for (ShoppingItem it : items) {
                if (!already.contains(it.id)) available.add(it);
            }
            return available;
        }, (List<ShoppingItem> available) -> {
            if (available == null || available.isEmpty()) {
                Toast.makeText(activity, "Все покупки этой вкладки уже добавлены",
                    Toast.LENGTH_LONG).show();
                return;
            }

            Set<Long> picked = new HashSet<>();

            View v = activity.getLayoutInflater()
                .inflate(R.layout.dialog_finance_shopping_pick, null);
            LinearLayout container = v.findViewById(R.id.container_shopping_pick);

            CheckBox selectAll = new CheckBox(activity);
            selectAll.setText("Выбрать всё");
            selectAll.setTextColor(ContextCompat.getColor(activity, R.color.neon_green));
            selectAll.setTextSize(14);
            selectAll.setPadding(0, dp(4), 0, dp(8));
            container.addView(selectAll);

            List<CheckBox> itemBoxes = new ArrayList<>();

            for (ShoppingItem it : available) {
                CheckBox cb = new CheckBox(activity);
                String pricePart = it.price != null
                    ? String.format(Locale.getDefault(), " — %.2f ₽", it.price)
                    : "";
                String statusPart = it.isBought ? " ✓ (куплено)" : "";
                String part = it.title + pricePart + statusPart;
                cb.setText(part);
                cb.setTextSize(14);
                cb.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
                cb.setPadding(0, dp(6), 0, dp(6));
                long id = it.id;
                cb.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) picked.add(id);
                    else picked.remove(id);
                });
                itemBoxes.add(cb);
                container.addView(cb);
            }

            selectAll.setOnCheckedChangeListener((b, checked) -> {
                for (int i = 0; i < itemBoxes.size(); i++) {
                    itemBoxes.get(i).setChecked(checked);
                }
            });

            new AlertDialog.Builder(activity)
                .setTitle("Покупки: " + info.label)
                .setView(v)
                .setPositiveButton("Добавить как план", (d, w) -> {
                    if (picked.isEmpty()) return;

                    List<FinanceExpense> toInsert = new ArrayList<>();
                    long now = System.currentTimeMillis();
                    for (ShoppingItem it : available) {
                        if (!picked.contains(it.id)) continue;
                        FinanceExpense e = new FinanceExpense();
                        e.periodId = pid;
                        e.category = it.category != null && !it.category.isEmpty()
                            ? it.category
                            : info.tabName;

                        // Title: "Раздел • Вкладка • Название" (+ пометка, если куплено)
                        StringBuilder t = new StringBuilder();
                        if (info.label != null && !info.label.isEmpty()) {
                            t.append(info.label);
                        } else if (info.tabName != null && !info.tabName.isEmpty()) {
                            t.append(info.tabName);
                        }
                        if (t.length() > 0) t.append(" • ");
                        t.append(it.title);
                        if (it.isBought) t.append(" ✓ (куплено)");
                        e.title = t.toString();

                        e.amount = it.price != null ? it.price : 0;
                        e.createdAt = now++;
                        e.sourceType = "shopping_bundle";
                        e.sourceRefId = null;
                        e.sourceRefs = String.valueOf(it.id);
                        toInsert.add(e);
                    }

                    DbExecutor.runVoid(() -> {
                        for (FinanceExpense e : toInsert) {
                            long id = expenseDao.insert(e);
                            if (id <= 0) android.util.Log.e("Finance",
                                "shopping insert failed, periodId=" + pid);
                        }
                    }, this::bindCurrentPeriod);
                })
                .setNegativeButton("Отмена", null)
                .show();
        });
    }

    private void bindCurrentPeriod() {
        TextView tvName = root.findViewById(R.id.tv_period_name);
        TextView tvRange = root.findViewById(R.id.tv_fin_period_range);
        TextView tvIncome = root.findViewById(R.id.tv_fin_income);
        TextView tvPlanned = root.findViewById(R.id.tv_fin_planned);
        TextView tvSpent = root.findViewById(R.id.tv_fin_spent);
        TextView tvLeft = root.findViewById(R.id.tv_fin_left);
        TextView empty = root.findViewById(R.id.tv_fin_empty);
        View card = root.findViewById(R.id.card_fin_summary);
        View plannedHeader = root.findViewById(R.id.tv_fin_planned_header);
        View factHeader = root.findViewById(R.id.tv_fin_fact_header);
        LinearLayout plannedContainer = root.findViewById(R.id.fin_planned_container);
        LinearLayout factContainer = root.findViewById(R.id.fin_fact_container);

        if (periods.isEmpty() || currentIndex < 0 || currentIndex >= periods.size()) {
            currentPeriod = null;
            tvName.setText("—");
            card.setVisibility(View.GONE);
            empty.setText("Нет периодов. Добавьте поступления в настройках.");
            empty.setVisibility(View.VISIBLE);
            plannedHeader.setVisibility(View.GONE);
            factHeader.setVisibility(View.GONE);
            plannedContainer.setVisibility(View.GONE);
            factContainer.setVisibility(View.GONE);
            plannedContainer.removeAllViews();
            factContainer.removeAllViews();
            return;
        }

        currentPeriod = periods.get(currentIndex);
        card.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        tvName.setText(currentPeriod.name);

        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

        String rangeText = df.format(new Date(currentPeriod.startAt))
            + " — "
            + df.format(new Date(currentPeriod.endAt));
        tvRange.setText(rangeText);

        tvIncome.setText(String.format(Locale.getDefault(), "Доход: %.2f ₽", currentPeriod.totalIncome));
        card.setOnLongClickListener(v -> {
            showPeriodActions();
            return true;
        });

        final long pid = currentPeriod.id;

        DbExecutor.run(() -> {
            List<FinanceExpense> all = expenseDao.getByPeriod(pid);
            List<FinanceExpense> planned = new ArrayList<>();
            List<FinanceExpense> fact = new ArrayList<>();
            double plannedSum = 0;
            double factSum = 0;
            for (FinanceExpense e : all) {
                if ("manual".equals(e.sourceType)) {
                    fact.add(e);
                    factSum += e.amount;
                } else {
                    planned.add(e);
                    plannedSum += e.amount;
                }
            }
            return new BindData(planned, fact, plannedSum, factSum);
        }, data -> {
            if (data == null) return;
            double left = currentPeriod.totalIncome - data.plannedSum - data.factSum;

            tvPlanned.setText(String.format(Locale.getDefault(),
                "Запланировано: %.2f ₽", data.plannedSum));
            tvSpent.setText(String.format(Locale.getDefault(),
                "Потрачено (факт): %.2f ₽", data.factSum));
            tvLeft.setText(String.format(Locale.getDefault(),
                "Свободно: %.2f ₽", left));
            tvLeft.setTextColor(ContextCompat.getColor(activity,
                left >= 0 ? R.color.neon_green : R.color.semantic_danger));

            bindForecast(data.factSum, data.plannedSum);

            plannedHeader.setVisibility(data.planned.isEmpty() ? View.GONE : View.VISIBLE);
            plannedContainer.setVisibility(data.planned.isEmpty() ? View.GONE : View.VISIBLE);
            bindExpensesList(plannedContainer, data.planned, true);

            factHeader.setVisibility(data.fact.isEmpty() ? View.GONE : View.VISIBLE);
            factContainer.setVisibility(data.fact.isEmpty() ? View.GONE : View.VISIBLE);
            bindExpensesList(factContainer, data.fact, false);
        });
    }

    private void bindExpensesList(LinearLayout container, List<FinanceExpense> list, boolean isPlanned) {
        container.removeAllViews();
        for (FinanceExpense e : list) {
            View item = activity.getLayoutInflater()
                .inflate(R.layout.item_finance_expense, container, false);
            TextView category = item.findViewById(R.id.fin_exp_category);
            TextView title = item.findViewById(R.id.fin_exp_title);
            TextView amount = item.findViewById(R.id.fin_exp_amount);

            category.setText(e.category);
            if (e.title != null && !e.title.isEmpty()) {
                title.setText(e.title);
                title.setVisibility(View.VISIBLE);
            } else {
                title.setVisibility(View.GONE);
            }
            amount.setText(String.format(Locale.getDefault(), "%.2f ₽", e.amount));

            if (isPlanned) {
                item.setOnClickListener(v -> showPlannedActions(e));
                item.setOnLongClickListener(v -> {
                    showPlannedActions(e);
                    return true;
                });
            } else {
                item.setOnClickListener(v -> showExpenseActions(e));
            }
            container.addView(item);
        }
    }

    private void bindForecast(double spent, double planned) {
        TextView tvAvgDay = root.findViewById(R.id.tv_fin_avg_day);
        TextView tvDaysLeft = root.findViewById(R.id.tv_fin_days_left);
        TextView tvCanSpend = root.findViewById(R.id.tv_fin_forecast);
        TextView tvPlanLeft = root.findViewById(R.id.tv_fin_plan_left);

        if (currentPeriod == null) {
            tvAvgDay.setText("Потрачено в день (факт): —");
            tvDaysLeft.setText("Осталось дней: —");
            tvCanSpend.setText("Можно тратить в день: —");
            tvCanSpend.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary));
            tvPlanLeft.setVisibility(View.GONE);
            return;
        }

        long now = System.currentTimeMillis();
        long start = currentPeriod.startAt;
        long end = currentPeriod.endAt;
        double dayMs = 24.0 * 60 * 60 * 1000;
        double daysTotal = (end - start) / dayMs;
        double elapsed = Math.max(0, Math.min(now, end) - start) / dayMs;
        double daysLeft = Math.max(0, daysTotal - elapsed);

        double factPerDay = elapsed > 0 ? spent / elapsed : 0;
        double freeNow = currentPeriod.totalIncome - planned - spent;
        double canSpendPerDay = daysLeft > 0 ? freeNow / daysLeft : 0;

        tvAvgDay.setText(String.format(Locale.getDefault(),
            "Потрачено в день (факт): %.2f ₽", factPerDay));
        tvDaysLeft.setText(String.format(Locale.getDefault(),
            "Осталось дней: %.0f", daysLeft));
        tvCanSpend.setText(String.format(Locale.getDefault(),
            "Можно тратить в день: %.2f ₽", canSpendPerDay));
        tvCanSpend.setTextColor(ContextCompat.getColor(activity,
            canSpendPerDay > 0 ? R.color.neon_green : R.color.semantic_danger));

        if (planned > 0) {
            tvPlanLeft.setText(String.format(Locale.getDefault(),
                "План ещё не выполнен: %.2f ₽", planned));
            tvPlanLeft.setVisibility(View.VISIBLE);
        } else {
            tvPlanLeft.setVisibility(View.GONE);
        }
    }

    private void showPeriodActions() {
        if (currentPeriod == null) return;
        new AlertDialog.Builder(activity)
            .setTitle(currentPeriod.name)
            .setItems(new String[]{
                "Удалить период (со всеми тратами)",
                "Переименовать",
                "Изменить доход"
            }, (d, which) -> {
                if (which == 0) confirmDeletePeriod();
                else if (which == 1) showRenamePeriodDialog();
                else showEditIncomeDialog();
            })
            .show();
    }

    private void confirmDeletePeriod() {
        new AlertDialog.Builder(activity)
            .setTitle("Удалить период «" + currentPeriod.name + "»?")
            .setMessage("Все траты этого периода тоже будут удалены.")
            .setPositiveButton("Удалить", (d, w) ->
                DbExecutor.runVoid(() -> periodDao.delete(currentPeriod.id), this::load))
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void showRenamePeriodDialog() {
        EditText input = new EditText(activity);
        input.setText(currentPeriod.name);
        new AlertDialog.Builder(activity)
            .setTitle("Название периода")
            .setView(input)
            .setPositiveButton("OK", (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                currentPeriod.name = name;
                DbExecutor.runVoid(() -> periodDao.update(currentPeriod), this::bindCurrentPeriod);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void showEditIncomeDialog() {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.2f", currentPeriod.totalIncome));
        new AlertDialog.Builder(activity)
            .setTitle("Доход за период, ₽")
            .setView(input)
            .setPositiveButton("OK", (d, w) -> {
                double v;
                try {
                    v = Double.parseDouble(input.getText().toString().trim().replace(',', '.'));
                    if (v < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    Toast.makeText(activity, "Некорректная сумма", Toast.LENGTH_SHORT).show();
                    return;
                }
                currentPeriod.totalIncome = v;
                DbExecutor.runVoid(() -> periodDao.update(currentPeriod), this::bindCurrentPeriod);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    public void showAddExpenseDialog() {
        if (currentPeriod == null) {
            Toast.makeText(activity, "Сначала настройте поступления во вкладке «Настройки»",
                Toast.LENGTH_LONG).show();
            return;
        }
        showExpenseDialog(null);
    }

    private void showExpenseActions(FinanceExpense e) {
        new AlertDialog.Builder(activity)
            .setTitle(e.category)
            .setItems(new String[]{
                "Отметить как план",
                "Редактировать",
                "Удалить"
            }, (d, w) -> {
                if (w == 0) {
                    DbExecutor.runVoid(() -> expenseDao.markAsPlanned(e.id), this::bindCurrentPeriod);
                } else if (w == 1) {
                    showExpenseDialog(e);
                } else {
                    DbExecutor.runVoid(() -> expenseDao.delete(e.id), this::bindCurrentPeriod);
                }
            })
            .show();
    }

    private void showPlannedActions(FinanceExpense e) {
        new AlertDialog.Builder(activity)
            .setTitle(e.category)
            .setItems(new String[]{
                "Отметить как потраченное",
                "Редактировать",
                "Удалить"
            }, (d, w) -> {
                if (w == 0) {
                    DbExecutor.runVoid(() -> expenseDao.markAsFact(e.id), this::bindCurrentPeriod);
                } else if (w == 1) {
                    showExpenseDialog(e);
                } else {
                    DbExecutor.runVoid(() -> expenseDao.delete(e.id), this::bindCurrentPeriod);
                }
            })
            .show();
    }

    private void showExpenseDialog(FinanceExpense existing) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_finance_expense, null);
        EditText inCat = v.findViewById(R.id.input_exp_category);
        EditText inTitle = v.findViewById(R.id.input_exp_title);
        EditText inAmt = v.findViewById(R.id.input_exp_amount);
        com.google.android.material.button.MaterialButton btnFact =
            v.findViewById(R.id.btn_type_fact);
        com.google.android.material.button.MaterialButton btnPlan =
            v.findViewById(R.id.btn_type_plan);

        // 0 = факт, 1 = план
        final int[] typeIndex = {0};
        if (existing != null && !"manual".equals(existing.sourceType)) {
            typeIndex[0] = 1;
        }

        Runnable applyType = () -> applySegButtons(btnFact, btnPlan, typeIndex[0]);
        applyType.run();
        btnFact.setOnClickListener(b -> {
            typeIndex[0] = 0;
            applyType.run();
        });
        btnPlan.setOnClickListener(b -> {
            typeIndex[0] = 1;
            applyType.run();
        });

        if (existing != null) {
            inCat.setText(existing.category);
            if (existing.title != null) inTitle.setText(existing.title);
            inAmt.setText(String.format(Locale.US, "%.2f", existing.amount));
        }

        new AlertDialog.Builder(activity)
            .setTitle(existing == null ? "Новая трата" : "Редактировать трату")
            .setView(v)
            .setPositiveButton("Сохранить", (d, w) -> {
                String cat = inCat.getText().toString().trim();
                String title = inTitle.getText().toString().trim();
                double amt;
                try {
                    amt = Double.parseDouble(inAmt.getText().toString().trim().replace(',', '.'));
                    if (amt < 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    Toast.makeText(activity, "Некорректная сумма", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (cat.isEmpty()) {
                    Toast.makeText(activity, "Введите категорию", Toast.LENGTH_SHORT).show();
                    return;
                }
                FinanceExpense e = existing != null ? existing : new FinanceExpense();
                e.periodId = currentPeriod.id;
                e.category = cat;
                e.title = title.isEmpty() ? null : title;
                e.amount = amt;
                if (typeIndex[0] == 0) {
                    e.sourceType = "manual";
                } else if (existing != null
                    && !"manual".equals(existing.sourceType)
                    && !"planned".equals(existing.sourceType)) {
                    // Не теряем привязку импортированной траты (шаблон/подписки/покупки).
                    e.sourceType = existing.sourceType;
                } else {
                    e.sourceType = "planned";
                }
                if (existing == null) {
                    e.createdAt = System.currentTimeMillis();
                }
                DbExecutor.runVoid(
                    () -> {
                        if (existing == null) expenseDao.insert(e);
                        else expenseDao.update(e);
                    },
                    this::bindCurrentPeriod
                );
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void applySegButtons(com.google.android.material.button.MaterialButton bFact,
                                 com.google.android.material.button.MaterialButton bPlan,
                                 int selectedIndex) {
        int accent = ContextCompat.getColor(activity, R.color.neon_green);
        int accentDim = ContextCompat.getColor(activity, R.color.neon_green_dim);
        int inactiveStroke = ContextCompat.getColor(activity, R.color.divider_strong);
        int inactiveText = ContextCompat.getColor(activity, R.color.text_secondary);

        com.google.android.material.button.MaterialButton[] all = {bFact, bPlan};
        for (int i = 0; i < all.length; i++) {
            com.google.android.material.button.MaterialButton b = all[i];
            boolean checked = (i == selectedIndex);
            b.setChecked(checked);
            if (checked) {
                b.setStrokeColor(android.content.res.ColorStateList.valueOf(accent));
                b.setTextColor(accent);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentDim & 0x33FFFFFF));
            } else {
                b.setStrokeColor(android.content.res.ColorStateList.valueOf(inactiveStroke));
                b.setTextColor(inactiveText);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
            }
        }
    }

    private void ensurePeriodsGenerated() {
        List<FinanceIncome> incomes = incomeDao.getAll(true);
        if (incomes.isEmpty()) return;

        List<Integer> days = extractSortedUniqueDays(incomes);
        Calendar now = Calendar.getInstance();

        for (int m = 0; m < 6; m++) {
            Calendar month = (Calendar) now.clone();
            month.add(Calendar.MONTH, m);
            int year = month.get(Calendar.YEAR);
            int mon = month.get(Calendar.MONTH);
            int maxDay = month.getActualMaximum(Calendar.DAY_OF_MONTH);

            List<Long> startDates = buildMonthStartDates(year, mon, maxDay, days);

            for (int i = 0; i < startDates.size(); i++) {
                long startAt = startDates.get(i);
                if (periodDao.findByStart(startAt) != null) continue;

                long endAt = (i + 1 < startDates.size())
                    ? startDates.get(i + 1)
                    : calcFirstStartOfNextMonth(year, mon, days.get(0));

                double sum = calcIncomeSumForStart(incomes, startAt, maxDay);
                String name = buildPeriodName(startAt, endAt);
                periodDao.insert(new FinancePeriod(startAt, endAt, name, sum));
            }
        }
    }

    private List<Integer> extractSortedUniqueDays(List<FinanceIncome> incomes) {
        List<Integer> days = new ArrayList<>();
        for (FinanceIncome inc : incomes) {
            if (!days.contains(inc.dayOfMonth)) {
                days.add(inc.dayOfMonth);
            }
        }
        days.sort(Integer::compareTo);
        return days;
    }

    private List<Long> buildMonthStartDates(int year, int month, int maxDay, List<Integer> days) {
        List<Long> startDates = new ArrayList<>(days.size());
        for (int day : days) {
            startDates.add(startOfDayMillis(year, month, Math.min(day, maxDay)));
        }
        startDates.sort(Long::compareTo);
        return startDates;
    }

    private long calcFirstStartOfNextMonth(int year, int month, int firstPayDay) {
        Calendar next = Calendar.getInstance();
        next.set(year, month, 1, 0, 0, 0);
        next.set(Calendar.MILLISECOND, 0);
        next.add(Calendar.MONTH, 1);

        int nextMaxDay = next.getActualMaximum(Calendar.DAY_OF_MONTH);
        int day = Math.min(firstPayDay, nextMaxDay);
        return startOfDayMillis(next.get(Calendar.YEAR), next.get(Calendar.MONTH), day);
    }

    private double calcIncomeSumForStart(List<FinanceIncome> incomes, long startAt, int maxDay) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startAt);
        int startDay = c.get(Calendar.DAY_OF_MONTH);

        double sum = 0;
        for (FinanceIncome inc : incomes) {
            if (startDay == Math.min(inc.dayOfMonth, maxDay)) {
                sum += inc.amount;
            }
        }
        return sum;
    }

    private long startOfDayMillis(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(year, month, day, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }


    private String buildPeriodName(long startAt, long endAt) {
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return df.format(new Date(startAt)) + " — " + df.format(new Date(endAt));
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }

    private record BindData(
        List<FinanceExpense> planned,
        List<FinanceExpense> fact,
        double plannedSum,
        double factSum
    ) {
    }

    private record LoadResult(List<FinancePeriod> periods, int index) {
    }

    private record SubPickResult(List<Subscription> subs, Set<Long> already) {
    }

    private static class TabShoppingInfo {
        long tabId;
        String tabName;
        String label;
        int count;
        int notBought;
        double sum;
    }

    private record ShoppingPickResult(List<TabShoppingInfo> infos, Set<Long> already) {
    }
}
