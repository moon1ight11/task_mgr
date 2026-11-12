package com.example.taskmgr.data;

import com.example.taskmgr.R;
import com.example.taskmgr.model.MainPlate;
import com.example.taskmgr.ui.finance.FinanceActivity;
import com.example.taskmgr.ui.notes.NotesActivity;
import com.example.taskmgr.ui.stats.StatsActivity;
import com.example.taskmgr.ui.subs.SubscriptionsActivity;
import com.example.taskmgr.ui.utilities.UtilitiesActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainPlatesCatalog {

    private static final Map<String, MainPlate> CATALOG = new LinkedHashMap<>();

    static {
        add(build("home", "Дом", R.drawable.ic_plate_home,
            MainPlate.Kind.SECTION, "Дом", null, false, true));
        add(build("garage", "Гараж", R.drawable.ic_plate_garage,
            MainPlate.Kind.SECTION, "Гараж", null, false, true));
        add(build("auto", "Авто", R.drawable.ic_plate_auto,
            MainPlate.Kind.SECTION, "Авто", null, true, true));
        add(build("it", "IT", R.drawable.ic_plate_it,
            MainPlate.Kind.SECTION, "IT", null, false, true));
        add(build("health", "Здоровье", R.drawable.ic_plate_health,
            MainPlate.Kind.SECTION, "Здоровье", null, false, true));
        add(build("ent", "Развлечения", R.drawable.ic_plate_ent,
            MainPlate.Kind.SECTION, "Развлечения", null, false, true));

        add(build("notes", "Заметки", R.drawable.ic_plate_notes,
            MainPlate.Kind.ACTIVITY, null, NotesActivity.class, true, false));
        add(build("private", "Private", R.drawable.ic_plate_private,
            MainPlate.Kind.PRIVATE, null, null, false, false));
        add(build("utilities", "ЖКХ", R.drawable.ic_plate_utilities,
            MainPlate.Kind.ACTIVITY, null, UtilitiesActivity.class, true, false));
        add(build("stats", "Статистика", R.drawable.ic_plate_stats,
            MainPlate.Kind.ACTIVITY, null, StatsActivity.class, false, false));
        add(build("subs", "Подписки", R.drawable.ic_plate_subs,
            MainPlate.Kind.ACTIVITY, null, SubscriptionsActivity.class, true, false));
        add(build("finance", "Финансы", R.drawable.ic_plate_finance,
            MainPlate.Kind.ACTIVITY, null, FinanceActivity.class, true, false));
    }

    private MainPlatesCatalog() {
    }

    private static MainPlate build(String key, String label, int iconRes,
                                   MainPlate.Kind kind, String sectionName,
                                   Class<?> activityClass,
                                   boolean tintGreen, boolean showBadge) {
        MainPlate p = new MainPlate();
        p.key = key;
        p.label = label;
        p.iconRes = iconRes;
        p.kind = kind;
        p.sectionName = sectionName;
        p.activityClass = activityClass;
        p.tintGreen = tintGreen;
        p.showBadge = showBadge;
        return p;
    }

    private static void add(MainPlate p) {
        CATALOG.put(p.key, p);
    }

    public static MainPlate get(String key) {
        return CATALOG.get(key);
    }

    public static List<MainPlate> all() {
        return new ArrayList<>(CATALOG.values());
    }

    public static List<String> allKeys() {
        return new ArrayList<>(CATALOG.keySet());
    }
}
