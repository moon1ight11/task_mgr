package com.example.taskmgr.ui.common;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.AppSettingsDao;
import com.example.taskmgr.service.NotificationScheduler;

import java.util.Locale;

public final class NotificationSettingsDialog {

    public static final int REQ_POST_NOTIFICATIONS = 9101;

    private NotificationSettingsDialog() {
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        AppSettingsDao dao = new AppSettingsDao(activity);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_notification_settings, null);
        View rowEvening = view.findViewById(R.id.row_evening);
        View rowMorning = view.findViewById(R.id.row_morning);
        com.google.android.material.materialswitch.MaterialSwitch swEvening = view.findViewById(R.id.switch_evening);
        com.google.android.material.materialswitch.MaterialSwitch swMorning = view.findViewById(R.id.switch_morning);
        TextView tvEvening = view.findViewById(R.id.time_evening);
        TextView tvMorning = view.findViewById(R.id.time_morning);

        boolean evEnabled = readBool(dao, AppSettingsDao.KEY_NOTIF_EVENING_ENABLED);
        boolean moEnabled = readBool(dao, AppSettingsDao.KEY_NOTIF_MORNING_ENABLED);
        int evH = readInt(dao, AppSettingsDao.KEY_NOTIF_EVENING_HOUR, AppSettingsDao.DEFAULT_EVENING_HOUR);
        int evM = readInt(dao, AppSettingsDao.KEY_NOTIF_EVENING_MINUTE, AppSettingsDao.DEFAULT_EVENING_MINUTE);
        int moH = readInt(dao, AppSettingsDao.KEY_NOTIF_MORNING_HOUR, AppSettingsDao.DEFAULT_MORNING_HOUR);
        int moM = readInt(dao, AppSettingsDao.KEY_NOTIF_MORNING_MINUTE, AppSettingsDao.DEFAULT_MORNING_MINUTE);

        int[] ev = {evH, evM};
        int[] mo = {moH, moM};

        swEvening.setChecked(evEnabled);
        swMorning.setChecked(moEnabled);
        tvEvening.setText(formatTime(ev[0], ev[1]));
        tvMorning.setText(formatTime(mo[0], mo[1]));
        rowEvening.setAlpha(evEnabled ? 1f : 0.5f);
        rowMorning.setAlpha(moEnabled ? 1f : 0.5f);

        swEvening.setOnCheckedChangeListener((b, checked) -> {
            rowEvening.setAlpha(checked ? 1f : 0.5f);
            if (checked) ensureNotificationPermission(activity);
        });
        swMorning.setOnCheckedChangeListener((b, checked) -> {
            rowMorning.setAlpha(checked ? 1f : 0.5f);
            if (checked) ensureNotificationPermission(activity);
        });

        tvEvening.setOnClickListener(v -> pickTime(activity, ev, tvEvening));
        tvMorning.setOnClickListener(v -> pickTime(activity, mo, tvMorning));

        new AlertDialog.Builder(activity).setTitle("Уведомления").setView(view).setPositiveButton("Сохранить", (d, w) -> {
            dao.put(AppSettingsDao.KEY_NOTIF_EVENING_ENABLED, swEvening.isChecked() ? "1" : "0");
            dao.put(AppSettingsDao.KEY_NOTIF_MORNING_ENABLED, swMorning.isChecked() ? "1" : "0");
            dao.put(AppSettingsDao.KEY_NOTIF_EVENING_HOUR, String.valueOf(ev[0]));
            dao.put(AppSettingsDao.KEY_NOTIF_EVENING_MINUTE, String.valueOf(ev[1]));
            dao.put(AppSettingsDao.KEY_NOTIF_MORNING_HOUR, String.valueOf(mo[0]));
            dao.put(AppSettingsDao.KEY_NOTIF_MORNING_MINUTE, String.valueOf(mo[1]));

            NotificationScheduler.scheduleEvening(activity);
            NotificationScheduler.scheduleMorning(activity);
            Toast.makeText(activity, "Сохранено", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Отмена", null).show();
    }

    private static void pickTime(Activity activity, int[] hm, TextView target) {
        new TimePickerDialog(activity, (tp, h, m) -> {
            hm[0] = h;
            hm[1] = m;
            target.setText(formatTime(h, m));
        }, hm[0], hm[1], true).show();
    }

    private static void ensureNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            return;
        activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
    }

    private static String formatTime(int h, int m) {
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    private static boolean readBool(AppSettingsDao dao, String key) {
        String v = dao.get(key);
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    private static int readInt(AppSettingsDao dao, String key, int def) {
        String v = dao.get(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
