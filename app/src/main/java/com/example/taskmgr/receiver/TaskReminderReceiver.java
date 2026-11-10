package com.example.taskmgr.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.taskmgr.R;
import com.example.taskmgr.service.DigestBuilder;
import com.example.taskmgr.service.NotificationScheduler;
import com.example.taskmgr.ui.main.MainActivity;

public class TaskReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_KIND = "kind";
    public static final String KIND_EVENING = "evening";
    public static final String KIND_MORNING = "morning";

    private static final String CHANNEL_EVENING = "digest_evening";
    private static final String CHANNEL_MORNING = "digest_morning";

    @Override
    public void onReceive(Context context, Intent intent) {
        String kind = intent.getStringExtra(EXTRA_KIND);
        if (kind == null) return;

        final PendingResult pr = goAsync();
        final Context appCtx = context.getApplicationContext();

        new Thread(() -> {
            try {
                handle(appCtx, kind);
            } finally {
                pr.finish();
            }
        }, "digest-build").start();
    }

    private void handle(Context context, String kind) {
        boolean evening = KIND_EVENING.equals(kind);

        if (evening) NotificationScheduler.scheduleEvening(context);
        else NotificationScheduler.scheduleMorning(context);

        DigestBuilder.Digest digest = DigestBuilder.build(context, evening);
        if (digest == null) return;

        String channelId = evening ? CHANNEL_EVENING : CHANNEL_MORNING;
        ensureChannel(context, channelId, evening);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
            context,
            evening ? 1001 : 1002,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF00FFB2)
            .setContentTitle(digest.title)
            .setContentText(digest.greeting)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(digest.bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context)
                .notify(evening ? 1001 : 1002, b.build());
        } catch (SecurityException ignored) {
        }
    }

    private void ensureChannel(Context context, String id, boolean evening) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel ch = new NotificationChannel(
            id,
            evening ? "Вечерняя сводка" : "Утренняя сводка",
            NotificationManager.IMPORTANCE_DEFAULT
        );
        ch.setDescription(evening
            ? "Задачи на завтра, каждый вечер"
            : "Задачи на сегодня, каждое утро");
        nm.createNotificationChannel(ch);
    }
}
