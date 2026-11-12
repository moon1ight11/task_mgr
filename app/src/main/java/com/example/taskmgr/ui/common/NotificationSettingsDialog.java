package com.example.taskmgr.ui.common;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.AppSettingsDao;
import com.example.taskmgr.service.NotificationScheduler;
import com.example.taskmgr.service.UpdateChecker;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public final class NotificationSettingsDialog {

    public static final int REQ_POST_NOTIFICATIONS = 9101;

    private NotificationSettingsDialog() {
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        AppSettingsDao dao = new AppSettingsDao(activity);

        View view = activity.getLayoutInflater()
            .inflate(R.layout.dialog_notification_settings, null);

        View rowEvening = view.findViewById(R.id.row_evening);
        View rowMorning = view.findViewById(R.id.row_morning);
        com.google.android.material.materialswitch.MaterialSwitch swEvening =
            view.findViewById(R.id.switch_evening);
        com.google.android.material.materialswitch.MaterialSwitch swMorning =
            view.findViewById(R.id.switch_morning);
        TextView tvEvening = view.findViewById(R.id.time_evening);
        TextView tvMorning = view.findViewById(R.id.time_morning);

        // Подписки
        com.google.android.material.materialswitch.MaterialSwitch swSubs =
            view.findViewById(R.id.switch_subs);
        View rowSubsDays = view.findViewById(R.id.row_subs_days);
        MaterialButton btnSubs1 = view.findViewById(R.id.btn_subs_1);
        MaterialButton btnSubs3 = view.findViewById(R.id.btn_subs_3);
        MaterialButton btnSubs7 = view.findViewById(R.id.btn_subs_7);

        // --- читаем настройки ---
        boolean evEnabled = readBool(dao, AppSettingsDao.KEY_NOTIF_EVENING_ENABLED);
        boolean moEnabled = readBool(dao, AppSettingsDao.KEY_NOTIF_MORNING_ENABLED);
        int evH = readInt(dao, AppSettingsDao.KEY_NOTIF_EVENING_HOUR, AppSettingsDao.DEFAULT_EVENING_HOUR);
        int evM = readInt(dao, AppSettingsDao.KEY_NOTIF_EVENING_MINUTE, AppSettingsDao.DEFAULT_EVENING_MINUTE);
        int moH = readInt(dao, AppSettingsDao.KEY_NOTIF_MORNING_HOUR, AppSettingsDao.DEFAULT_MORNING_HOUR);
        int moM = readInt(dao, AppSettingsDao.KEY_NOTIF_MORNING_MINUTE, AppSettingsDao.DEFAULT_MORNING_MINUTE);

        boolean subsEnabled = readBool(dao, AppSettingsDao.KEY_NOTIF_SUBS_ENABLED);
        int subsDays = readInt(dao, AppSettingsDao.KEY_NOTIF_SUBS_DAYS, AppSettingsDao.DEFAULT_SUBS_DAYS);

        final int[] ev = {evH, evM};
        final int[] mo = {moH, moM};
        final int[] subsDaysState = {subsDays};

        // --- заполняем UI ---
        swEvening.setChecked(evEnabled);
        swMorning.setChecked(moEnabled);
        swSubs.setChecked(subsEnabled);
        tvEvening.setText(formatTime(ev[0], ev[1]));
        tvMorning.setText(formatTime(mo[0], mo[1]));

        rowEvening.setAlpha(evEnabled ? 1f : 0.5f);
        rowMorning.setAlpha(moEnabled ? 1f : 0.5f);
        rowSubsDays.setAlpha(subsEnabled ? 1f : 0.5f);

        // --- кнопки 1/3/7 ---
        List<MaterialButton> dayButtons = List.of(btnSubs1, btnSubs3, btnSubs7);
        int[] dayValues = {1, 3, 7};

        Runnable refreshDays = () -> {
            for (int i = 0; i < dayButtons.size(); i++) {
                applySegButton(activity, dayButtons.get(i), dayValues[i] == subsDaysState[0]);
            }
        };
        btnSubs1.setOnClickListener(v -> { subsDaysState[0] = 1; refreshDays.run(); });
        btnSubs3.setOnClickListener(v -> { subsDaysState[0] = 3; refreshDays.run(); });
        btnSubs7.setOnClickListener(v -> { subsDaysState[0] = 7; refreshDays.run(); });
        refreshDays.run();

        // --- слушатели ---
        swEvening.setOnCheckedChangeListener((b, checked) -> {
            rowEvening.setAlpha(checked ? 1f : 0.5f);
            if (checked) ensureNotificationPermission(activity);
        });
        swMorning.setOnCheckedChangeListener((b, checked) -> {
            rowMorning.setAlpha(checked ? 1f : 0.5f);
            if (checked) ensureNotificationPermission(activity);
        });
        swSubs.setOnCheckedChangeListener((b, checked) -> {
            rowSubsDays.setAlpha(checked ? 1f : 0.5f);
            if (checked) ensureNotificationPermission(activity);
        });

        tvEvening.setOnClickListener(v -> pickTime(activity, ev, tvEvening));
        tvMorning.setOnClickListener(v -> pickTime(activity, mo, tvMorning));

        view.findViewById(R.id.btn_check_updates).setOnClickListener(v ->
            checkUpdates(activity));

        new AlertDialog.Builder(activity).setTitle("Уведомления").setView(view).setPositiveButton("Сохранить", (d, w) -> {
            dao.put(AppSettingsDao.KEY_NOTIF_EVENING_ENABLED, swEvening.isChecked() ? "1" : "0");
            dao.put(AppSettingsDao.KEY_NOTIF_MORNING_ENABLED, swMorning.isChecked() ? "1" : "0");
            dao.put(AppSettingsDao.KEY_NOTIF_EVENING_HOUR, String.valueOf(ev[0]));
            dao.put(AppSettingsDao.KEY_NOTIF_EVENING_MINUTE, String.valueOf(ev[1]));
            dao.put(AppSettingsDao.KEY_NOTIF_MORNING_HOUR, String.valueOf(mo[0]));
            dao.put(AppSettingsDao.KEY_NOTIF_MORNING_MINUTE, String.valueOf(mo[1]));
            dao.put(AppSettingsDao.KEY_NOTIF_SUBS_ENABLED, swSubs.isChecked() ? "1" : "0");
            dao.put(AppSettingsDao.KEY_NOTIF_SUBS_DAYS, String.valueOf(subsDaysState[0]));

            NotificationScheduler.scheduleEvening(activity);
            NotificationScheduler.scheduleMorning(activity);
            Toast.makeText(activity, "Сохранено", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Отмена", null).show();
    }

    private static void applySegButton(Activity activity, MaterialButton b, boolean checked) {
        int accent = ContextCompat.getColor(activity, R.color.neon_green);
        int accentDim = ContextCompat.getColor(activity, R.color.neon_green_dim);
        int inactiveStroke = ContextCompat.getColor(activity, R.color.divider_strong);
        int inactiveText = ContextCompat.getColor(activity, R.color.text_secondary);

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

    private static void pickTime(Activity activity, int[] hm, TextView target) {
        new TimePickerDialog(activity, (tp, h, m) -> {
            hm[0] = h;
            hm[1] = m;
            target.setText(formatTime(h, m));
        }, hm[0], hm[1], true).show();
    }

    private static void checkUpdates(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Toast.makeText(activity, "Проверка обновлений…", Toast.LENGTH_SHORT).show();

        UpdateChecker.check(com.example.taskmgr.BuildConfig.VERSION_NAME, info -> {
            if (activity.isFinishing()) return;

            if (info == null) {
                new AlertDialog.Builder(activity)
                    .setTitle("Обновлений нет")
                    .setMessage("У вас уже установлена последняя версия.")
                    .setPositiveButton("OK", null)
                    .show();
                return;
            }

            new AlertDialog.Builder(activity)
                .setTitle("Доступно обновление " + info.version)
                .setMessage(info.releaseNotes != null && !info.releaseNotes.isEmpty()
                    ? info.releaseNotes
                    : "Скачать новую версию?")
                .setPositiveButton("Скачать", (d, w) -> downloadAndInstall(activity, info.apkUrl))
                .setNegativeButton("Позже", null)
                .show();
        });
    }

    private static void downloadAndInstall(Activity activity, String apkUrl) {
        Toast.makeText(activity, "Скачивание…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            java.io.File apk = new java.io.File(activity.getCacheDir(), "update.apk");
            java.net.HttpURLConnection conn = null;
            try {
                java.net.URL url = new java.net.URL(apkUrl);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();

                if (conn.getResponseCode() != 200) {
                    throw new java.io.IOException("HTTP " + conn.getResponseCode());
                }

                try (java.io.InputStream in = conn.getInputStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(apk)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }

                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing()) installApk(activity, apk);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                    Toast.makeText(activity, "Ошибка скачивания: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static void installApk(Activity activity, java.io.File apk) {
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apk
            );

            android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Не удалось запустить установку: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
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
