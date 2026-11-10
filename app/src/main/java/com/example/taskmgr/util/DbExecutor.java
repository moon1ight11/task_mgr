package com.example.taskmgr.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class DbExecutor {

    private static final String TAG = "DbExecutor";

    private static final ExecutorService IO =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "db-io");
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DbExecutor() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onResult) {
        IO.execute(() -> {
            try {
                T result = work.call();
                if (onResult != null) {
                    MAIN.post(() -> onResult.accept(result));
                }
            } catch (Exception e) {
                Log.e(TAG, "DB task failed", e);
                if (onResult != null) {
                    MAIN.post(() -> onResult.accept(null));
                }
            }
        });
    }

    public static void runVoid(Runnable work, Runnable onDone) {
        IO.execute(() -> {
            try {
                work.run();
            } catch (Exception e) {
                Log.e(TAG, "DB void task failed", e);
            } finally {
                if (onDone != null) {
                    MAIN.post(onDone);
                }
            }
        });
    }
}
