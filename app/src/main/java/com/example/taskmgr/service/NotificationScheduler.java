package com.example.taskmgr.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.taskmgr.data.dao.AppSettingsDao;
import com.example.taskmgr.receiver.TaskReminderReceiver;

import java.util.Calendar;

public final class NotificationScheduler {

    private static final int REQ_EVENING = 2001;
    private static final int REQ_MORNING = 2002;

    private NotificationScheduler() {
    }

    public static void scheduleAll(Context context) {
        scheduleEvening(context);
        scheduleMorning(context);
    }

    public static void scheduleEvening(Context context) {
        AppSettingsDao dao = new AppSettingsDao(context);
        boolean enabled = readBool(dao, AppSettingsDao.KEY_NOTIF_EVENING_ENABLED);
        if (!enabled) {
            cancel(context, REQ_EVENING, TaskReminderReceiver.KIND_EVENING);
            return;
        }
        int h = readInt(dao, AppSettingsDao.KEY_NOTIF_EVENING_HOUR,
            AppSettingsDao.DEFAULT_EVENING_HOUR);
        int m = readInt(dao, AppSettingsDao.KEY_NOTIF_EVENING_MINUTE,
            AppSettingsDao.DEFAULT_EVENING_MINUTE);
        schedule(context, REQ_EVENING, TaskReminderReceiver.KIND_EVENING, h, m);
    }

    public static void scheduleMorning(Context context) {
        AppSettingsDao dao = new AppSettingsDao(context);
        boolean enabled = readBool(dao, AppSettingsDao.KEY_NOTIF_MORNING_ENABLED);
        if (!enabled) {
            cancel(context, REQ_MORNING, TaskReminderReceiver.KIND_MORNING);
            return;
        }
        int h = readInt(dao, AppSettingsDao.KEY_NOTIF_MORNING_HOUR,
            AppSettingsDao.DEFAULT_MORNING_HOUR);
        int m = readInt(dao, AppSettingsDao.KEY_NOTIF_MORNING_MINUTE,
            AppSettingsDao.DEFAULT_MORNING_MINUTE);
        schedule(context, REQ_MORNING, TaskReminderReceiver.KIND_MORNING, h, m);
    }

    private static void schedule(Context context, int reqCode, String kind, int hour, int minute) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(context, TaskReminderReceiver.class);
        i.putExtra(TaskReminderReceiver.EXTRA_KIND, kind);

        PendingIntent pi = PendingIntent.getBroadcast(
            context, reqCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long at = nextTrigger(hour, minute);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static void cancel(Context context, int reqCode, String kind) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, TaskReminderReceiver.class);
        i.putExtra(TaskReminderReceiver.EXTRA_KIND, kind);
        PendingIntent pi = PendingIntent.getBroadcast(
            context, reqCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.cancel(pi);
        pi.cancel();
    }

    private static long nextTrigger(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long at = c.getTimeInMillis();
        long now = System.currentTimeMillis();
        if (at <= now) at += 24L * 60 * 60 * 1000;
        return at;
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
