package com.example.taskmgr.model;

public class MainPlate {

    public enum Kind { SECTION, PRIVATE, ACTIVITY }

    public String key;          // "home", "auto", "notes", ...
    public String label;        // "Дом", "Авто", ...
    public int iconRes;         // R.drawable.ic_plate_home, ...
    public Kind kind;
    public String sectionName;  // для Kind.SECTION — имя раздела
    public Class<?> activityClass; // для Kind.ACTIVITY
    public boolean tintGreen;   // зелёная иконка (Авто, Заметки, ЖКХ, Подписки, Финансы)
    public boolean showBadge;   // показывать счётчик активных задач (Дом, Гараж, Авто, IT, Здоровье, Развлечения)

    public boolean isVisible = true;
    public int sortOrder;

    public MainPlate() {
    }
}
