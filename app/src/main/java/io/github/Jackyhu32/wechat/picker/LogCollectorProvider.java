package io.github.Jackyhu32.wechat.picker;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import java.io.File;

/**
 * Receives diagnostic log lines from our hook running inside {@code com.tencent.mm}
 * so the user can toggle recording, copy, or share logs directly from {@link SettingsActivity}
 * without needing ADB or root.
 */
public class LogCollectorProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.Jackyhu32.wechat.picker.logs";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_APPEND = "append";
    public static final String EXTRA_LINE = "line";
    public static final String LOG_FILE_NAME = "diagnostics.log";

    public static File logFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE_NAME);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null || !METHOD_APPEND.equals(method) || extras == null) {
            return null;
        }
        if (!isAllowedCaller(context)) {
            return null;
        }
        String line = extras.getString(EXTRA_LINE);
        if (line != null && !line.isEmpty()) {
            LogStore.append(logFile(context), line);
        }
        return Bundle.EMPTY;
    }

    private boolean isAllowedCaller(Context context) {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) {
            return true;
        }
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return false;
        }
        String[] pkgs = pm.getPackagesForUid(uid);
        if (pkgs == null) {
            return false;
        }
        for (String pkg : pkgs) {
            if (MainHook.TARGET.equals(pkg) || ModulePrefs.PKG.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
