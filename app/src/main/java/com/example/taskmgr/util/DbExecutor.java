package com.example.taskmgr.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Единый фоновый поток для работы с SQLite.
 * SQLite любит последовательный доступ — один поток решает проблему.
 */
public final class DbExecutor {

    private static final ExecutorService IO =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "db-io");
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DbExecutor() {}

    /** Выполнить в фоне; результат — в main thread. */
    public static <T> void run(Callable<T> work, Consumer<T> onResult) {
        IO.execute(() -> {
            try {
                T result = work.call();
                if (onResult != null) {
                    MAIN.post(() -> onResult.accept(result));
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (onResult != null) {
                    MAIN.post(() -> onResult.accept(null));
                }
            }
        });
    }

    /** Выполнить в фоне без результата. */
    public static void run(Runnable work) {
        IO.execute(work);
    }

    /**
     * Выполнить в фоне, затем в main thread вызвать onDone (даже при ошибке).
     * Удобно для «сделал в БД → перезагрузил список».
     */
    public static void runVoid(Runnable work, Runnable onDone) {
        IO.execute(() -> {
            try {
                work.run();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (onDone != null) {
                    MAIN.post(onDone);
                }
            }
        });
    }

    /** Выполнить в main thread. */
    public static void postToMain(Runnable r) {
        MAIN.post(r);
    }
}