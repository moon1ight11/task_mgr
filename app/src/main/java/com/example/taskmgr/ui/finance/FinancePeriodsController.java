package com.example.taskmgr.ui.finance;

import android.app.Activity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private final FinanceExpensesAdapter adapter;

    private List<FinancePeriod> periods = new ArrayList<>();
    private final List<FinanceTemplate> templatesForPick = new ArrayList<>();
    private final Set<Long> pickedTemplateIds = new HashSet<>();
    private final Set<Long> pickedSubIds = new HashSet<>();
    private int currentIndex = 0;
    private FinancePeriod currentPeriod;

    public FinancePeriodsController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        incomeDao = new FinanceIncomeDao(activity);
        periodDao = new FinancePeriodDao(activity);
        expenseDao = new FinanceExpenseDao(activity);
        templateDao = new FinanceTemplateDao(activity);

        RecyclerView recycler = root.findViewById(R.id.fin_expenses_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new FinanceExpensesAdapter(this::showExpenseActions);
        recycler.setAdapter(adapter);

        root.findViewById(R.id.btn_prev_period).setOnClickListener(v -> {
            if (currentIndex > 0) { currentIndex--; bindCurrentPeriod(); }
        });
        root.findViewById(R.id.btn_next_period).setOnClickListener(v -> {
            if (currentIndex < periods.size() - 1) { currentIndex++; bindCurrentPeriod(); }
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
                if (list.get(i).startAt <= now && list.get(i).endAt > now) { idx = i; break; }
            }
            return new LoadResult(list, idx);
        }, res -> {
            if (res == null) return;
            periods = res.periods;
            currentIndex = res.index;
            bindCurrentPeriod();
        });
    }

    // ---------------- шаблоны ----------------

    public void showPickTemplatesDialog() {
        if (currentPeriod == null) {
            Toast.makeText(activity, "Сначала выберите период", Toast.LENGTH_SHORT).show();
            return;
        }
        DbExecutor.run(
            (java.util.concurrent.Callable<List<FinanceTemplate>>) () -> templateDao.getAll(true),
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
                    cb.setText(catPart + subPart + amountPart);
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
                                e.sourceType = "manual";
                                expenseDao.insert(e);
                            }
                        }, this::bindCurrentPeriod);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            }
        );
    }

    // ---------------- подписки ----------------

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
            for (Subscription s : subs) {
                if (!already.contains(s.id)) available.add(s);
            }
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

    // ---------------- покупки ----------------

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
            for (Section s : sectionDao.getAll()) sectionNames.put(s.id, s.name);

            List<TabShoppingInfo> infos = new ArrayList<>();
            for (Tab t : allTabs) {
                if (!t.showShopping) continue;
                List<ShoppingItem> items = shopDao.getByTabSorted(
                    t.id, null, ShoppingDao.SortMode.CREATED, true);
                if (items.isEmpty()) continue;
                double sum = 0;
                for (ShoppingItem it : items) {
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
                labels[i] = info.label + " · " + info.count + " поз. · "
                    + String.format(Locale.getDefault(), "%.2f ₽", info.sum);
            }
            new AlertDialog.Builder(activity)
                .setTitle("Вкладка с покупками")
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

            for (ShoppingItem it : available) {
                CheckBox cb = new CheckBox(activity);
                String pricePart = it.price != null
                    ? String.format(Locale.getDefault(), " — %.2f ₽", it.price)
                    : "";
                String statusPart = it.isBought ? " ✓" : "";
                cb.setText(it.title + pricePart + statusPart);
                cb.setTextSize(14);
                cb.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
                cb.setPadding(0, dp(6), 0, dp(6));
                long id = it.id;
                cb.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) picked.add(id);
                    else picked.remove(id);
                });
                container.addView(cb);
            }

            new AlertDialog.Builder(activity)
                .setTitle("Покупки: " + info.label)
                .setView(v)
                .setPositiveButton("Добавить", (d, w) -> {
                    if (picked.isEmpty()) return;
                    StringBuilder names = new StringBuilder();
                    StringBuilder refs = new StringBuilder();
                    double total = 0;
                    for (ShoppingItem it : available) {
                        if (!picked.contains(it.id)) continue;
                        if (names.length() > 0) names.append(", ");
                        names.append(it.title);
                        if (refs.length() > 0) refs.append(",");
                        refs.append(it.id);
                        if (it.price != null) total += it.price;
                    }
                    final double totalFinal = total;
                    final String namesFinal = names.toString();
                    final String refsFinal = refs.toString();
                    final String category = info.tabName;

                    DbExecutor.runVoid(() -> {
                        FinanceExpense e = new FinanceExpense();
                        e.periodId = pid;
                        e.category = category;
                        e.title = namesFinal;
                        e.amount = totalFinal;
                        e.createdAt = System.currentTimeMillis();
                        e.sourceType = "shopping_bundle";
                        e.sourceRefId = null;
                        e.sourceRefs = refsFinal;
                        expenseDao.insert(e);
                    }, this::bindCurrentPeriod);
                })
                .setNegativeButton("Отмена", null)
                .show();
        });
    }

    // ---------------- bind ----------------

    private void bindCurrentPeriod() {
        TextView tvName = root.findViewById(R.id.tv_period_name);
        TextView tvRange = root.findViewById(R.id.tv_fin_period_range);
        TextView tvIncome = root.findViewById(R.id.tv_fin_income);
        TextView tvSpent = root.findViewById(R.id.tv_fin_spent);
        TextView tvLeft = root.findViewById(R.id.tv_fin_left);
        TextView empty = root.findViewById(R.id.tv_fin_empty);
        RecyclerView recycler = root.findViewById(R.id.fin_expenses_recycler);
        View card = root.findViewById(R.id.card_fin_summary);

        if (periods.isEmpty() || currentIndex < 0 || currentIndex >= periods.size()) {
            currentPeriod = null;
            tvName.setText("—");
            card.setVisibility(View.GONE);
            empty.setText("Нет периодов. Добавьте поступления в настройках.");
            empty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            adapter.setItems(new ArrayList<>());
            return;
        }

        currentPeriod = periods.get(currentIndex);
        card.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);

        tvName.setText(currentPeriod.name);

        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        tvRange.setText(df.format(new Date(currentPeriod.startAt)) + " — "
            + df.format(new Date(currentPeriod.endAt)));

        tvIncome.setText(String.format(Locale.getDefault(), "Доход: %.2f ₽", currentPeriod.totalIncome));
        card.setOnLongClickListener(v -> {
            showPeriodActions();
            return true;
        });

        final long pid = currentPeriod.id;
        final FinancePeriod prevPeriod = currentIndex > 0 ? periods.get(currentIndex - 1) : null;

        DbExecutor.run(() -> {
            List<FinanceExpense> current = expenseDao.getByPeriod(pid);
            double currentSpent = 0;
            for (FinanceExpense e : current) currentSpent += e.amount;
            double prevSpent = 0;
            if (prevPeriod != null) {
                List<FinanceExpense> prevList = expenseDao.getByPeriod(prevPeriod.id);
                for (FinanceExpense e : prevList) prevSpent += e.amount;
            }
            return new BindData(current, currentSpent, prevSpent);
        }, data -> {
            if (data == null) return;
            double spent = data.currentSpent;
            double left = currentPeriod.totalIncome - spent;

            tvSpent.setText(String.format(Locale.getDefault(), "Потрачено: %.2f ₽", spent));
            tvLeft.setText(String.format(Locale.getDefault(), "Остаток: %.2f ₽", left));
            tvLeft.setTextColor(ContextCompat.getColor(activity,
                left >= 0 ? R.color.neon_green : R.color.semantic_danger));

            Score score = computeScore(currentPeriod, spent, data.current,
                prevPeriod, data.prevSpent);
            bindScore(score);

            adapter.setItems(data.current);
        });
    }

    private void bindScore(Score score) {
        TextView tvLabel = root.findViewById(R.id.tv_fin_score_label);
        ProgressBar pb = root.findViewById(R.id.pb_fin_score);
        TextView tvSub = root.findViewById(R.id.tv_fin_score_subtitle);
        TextView tvTrend = root.findViewById(R.id.tv_fin_score_trend);

        if (score == null) {
            tvLabel.setText("Оценка: —");
            pb.setProgress(0);
            tvSub.setText("—");
            tvTrend.setVisibility(View.GONE);
            return;
        }

        int color = ContextCompat.getColor(activity, score.colorRes);
        tvLabel.setText(String.format(Locale.getDefault(), "Оценка: %d/100 · %s",
            score.value, score.label));
        tvLabel.setTextColor(color);
        pb.setProgress(score.value);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        tvSub.setText(score.subtitle);

        if (score.trendText != null && !score.trendText.isEmpty()) {
            tvTrend.setText(score.trendText);
            tvTrend.setVisibility(View.VISIBLE);
        } else {
            tvTrend.setVisibility(View.GONE);
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

    // ---------------- оценка ----------------

    private Score computeScore(FinancePeriod period, double spent, List<FinanceExpense> expenses,
                               FinancePeriod prevPeriod, double prevSpent) {
        Score s = new Score();
        if (period.totalIncome <= 0) {
            s.value = 0;
            s.label = "—";
            s.colorRes = R.color.text_tertiary;
            s.subtitle = "Введите доход в настройках";
            s.trendText = "";
            return s;
        }

        double left = period.totalIncome - spent;
        double leftPct = left / period.totalIncome * 100.0;

        double score = Math.max(0, Math.min(100, leftPct));

        if (leftPct >= 30) score += 15;
        else if (leftPct >= 20) score += 10;

        double hardSum = 0;
        for (FinanceExpense e : expenses) {
            if (isHardCategory(e.category)) hardSum += e.amount;
        }
        if (hardSum / period.totalIncome > 0.7) score -= 5;

        score = Math.max(0, Math.min(100, score));
        s.value = (int) Math.round(score);

        if (s.value >= 70) { s.label = "Отлично"; s.colorRes = R.color.neon_green; }
        else if (s.value >= 50) { s.label = "Хорошо"; s.colorRes = R.color.neon_green; }
        else if (s.value >= 30) { s.label = "Средне"; s.colorRes = R.color.semantic_warning; }
        else { s.label = "Плохо"; s.colorRes = R.color.semantic_danger; }

        s.subtitle = String.format(Locale.getDefault(),
            "В копилку: %.2f ₽ (%.0f%%)", left, leftPct);

        if (prevPeriod != null && prevPeriod.totalIncome > 0) {
            double prevLeft = prevPeriod.totalIncome - prevSpent;
            double prevPct = prevLeft / prevPeriod.totalIncome * 100.0;
            double delta = leftPct - prevPct;
            if (Math.abs(delta) >= 0.5) {
                s.trendText = String.format(Locale.getDefault(),
                    "%s%.1f%% к прошлому периоду", delta >= 0 ? "+" : "", delta);
            } else {
                s.trendText = "Как в прошлом периоде";
            }
        } else {
            s.trendText = "";
        }
        return s;
    }

    private boolean isHardCategory(String cat) {
        if (cat == null) return false;
        String c = cat.toLowerCase(Locale.getDefault());
        return c.contains("аренд") || c.contains("жкх") || c.contains("кредит")
            || c.contains("подписк") || c.contains("ипотек");
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
            .setItems(new String[]{"Редактировать", "Удалить"}, (d, w) -> {
                if (w == 0) showExpenseDialog(e);
                else {
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
                if (existing == null) {
                    e.createdAt = System.currentTimeMillis();
                    e.sourceType = "manual";
                }
                DbExecutor.runVoid(
                    () -> { if (existing == null) expenseDao.insert(e); else expenseDao.update(e); },
                    this::bindCurrentPeriod
                );
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    // ---------------- генерация периодов ----------------

    private void ensurePeriodsGenerated() {
        List<FinanceIncome> incomes = incomeDao.getAll(true);
        if (incomes.isEmpty()) return;

        List<Integer> days = new ArrayList<>();
        for (FinanceIncome i : incomes) {
            if (!days.contains(i.dayOfMonth)) days.add(i.dayOfMonth);
        }
        days.sort(Integer::compareTo);

        Calendar now = Calendar.getInstance();
        for (int m = 0; m < 6; m++) {
            Calendar month = (Calendar) now.clone();
            month.add(Calendar.MONTH, m);
            int year = month.get(Calendar.YEAR);
            int mon = month.get(Calendar.MONTH);
            int maxDay = month.getActualMaximum(Calendar.DAY_OF_MONTH);

            List<Long> startDates = new ArrayList<>();
            for (int day : days) {
                int d = Math.min(day, maxDay);
                Calendar c = Calendar.getInstance();
                c.set(year, mon, d, 0, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                startDates.add(c.getTimeInMillis());
            }
            startDates.sort(Long::compareTo);

            for (int i = 0; i < startDates.size(); i++) {
                long startAt = startDates.get(i);
                long endAt;
                if (i + 1 < startDates.size()) {
                    endAt = startDates.get(i + 1);
                } else {
                    Calendar next = Calendar.getInstance();
                    next.set(year, mon, 1, 0, 0, 0);
                    next.set(Calendar.MILLISECOND, 0);
                    next.add(Calendar.MONTH, 1);
                    int nextYear = next.get(Calendar.YEAR);
                    int nextMonth = next.get(Calendar.MONTH);
                    int nextMaxDay = next.getActualMaximum(Calendar.DAY_OF_MONTH);
                    int firstDay = Math.min(days.get(0), nextMaxDay);
                    Calendar c = Calendar.getInstance();
                    c.set(nextYear, nextMonth, firstDay, 0, 0, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    endAt = c.getTimeInMillis();
                }
                if (periodDao.findByStart(startAt) != null) continue;

                double sum = 0;
                for (FinanceIncome inc : incomes) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(startAt);
                    if (c.get(Calendar.DAY_OF_MONTH) == Math.min(inc.dayOfMonth, maxDay)) {
                        sum += inc.amount;
                    }
                }

                String name = buildPeriodName(startAt, endAt);
                FinancePeriod p = new FinancePeriod(startAt, endAt, name, sum);
                periodDao.insert(p);
            }
        }
    }

    private String buildPeriodName(long startAt, long endAt) {
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return df.format(new Date(startAt)) + " — " + df.format(new Date(endAt));
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }

    // ---------------- вложенные классы ----------------

    private static class Score {
        int value;
        String label;
        int colorRes;
        String subtitle;
        String trendText;
    }

    private static class BindData {
        final List<FinanceExpense> current;
        final double currentSpent;
        final double prevSpent;
        BindData(List<FinanceExpense> c, double cs, double ps) {
            current = c; currentSpent = cs; prevSpent = ps;
        }
    }

    private static class LoadResult {
        final List<FinancePeriod> periods;
        final int index;
        LoadResult(List<FinancePeriod> p, int i) { periods = p; index = i; }
    }

    private static class SubPickResult {
        final List<Subscription> subs;
        final Set<Long> already;
        SubPickResult(List<Subscription> s, Set<Long> a) { subs = s; already = a; }
    }

    private static class TabShoppingInfo {
        long tabId;
        String tabName;
        String label;
        int count;
        double sum;
    }

    private static class ShoppingPickResult {
        final List<TabShoppingInfo> infos;
        final Set<Long> already;
        ShoppingPickResult(List<TabShoppingInfo> i, Set<Long> a) { infos = i; already = a; }
    }
}
