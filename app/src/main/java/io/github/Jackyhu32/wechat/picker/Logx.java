package io.github.Jackyhu32.wechat.picker;

import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XposedBridge;

final class Logx {
    static final String TAG = "WeChatSysPicker";
    private static final long PREF_REFRESH_MS = 2000L;
    private static volatile long lastCheckMs;
    private static volatile boolean recordEnabled;
    private static final ExecutorService LOG_IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WeChatSysPicker-Log");
        thread.setDaemon(true);
        return thread;
    });

    private Logx() {
    }

    static void i(String message) {
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
        }
        try {
            XposedBridge.log(TAG + ": " + message);
        } catch (Throwable ignored) {
        }
        if (isRecordingEnabled()) {
            forwardToModule(message);
        }
    }

    private static boolean isRecordingEnabled() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs > PREF_REFRESH_MS) {
            lastCheckMs = now;
            try {
                recordEnabled = ModulePrefs.load().recordLogs;
            } catch (Throwable ignored) {
                recordEnabled = false;
            }
        }
        return recordEnabled;
    }

    private static void forwardToModule(String message) {
        final String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        final String line = timestamp + " " + message;
        LOG_IO.execute(() -> {
            try {
                Application app = android.app.AndroidAppHelper.currentApplication();
                if (app == null || app.getContentResolver() == null) {
                    return;
                }
                Bundle extras = new Bundle();
                extras.putString(LogCollectorProvider.EXTRA_LINE, line);
                app.getContentResolver().call(
                        LogCollectorProvider.CONTENT_URI,
                        LogCollectorProvider.METHOD_APPEND,
                        null,
                        extras
                );
            } catch (Throwable ignored) {
            }
        });
    }
}
