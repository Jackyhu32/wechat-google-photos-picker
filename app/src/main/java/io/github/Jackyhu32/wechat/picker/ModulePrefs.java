package io.github.Jackyhu32.wechat.picker;

import android.content.SharedPreferences;

import de.robv.android.xposed.XSharedPreferences;

/**
 * Defaults stay on when LSPosed has not published a world-readable file yet,
 * so a fresh install already redirects the album.
 */
public final class ModulePrefs {
    public static final String PKG = "io.github.Jackyhu32.wechat.picker";
    public static final String NAME = "config";
    public static final String KEY_REPLACE = "replace_picker";
    public static final String KEY_BUTTON = "show_button";
    public static final String KEY_RAW = "force_raw";
    public static final String KEY_LOG = "record_logs";

    public final boolean replacePicker;
    public final boolean showButton;
    public final boolean forceRaw;
    public final boolean recordLogs;

    public ModulePrefs(boolean replacePicker, boolean showButton, boolean forceRaw) {
        this(replacePicker, showButton, forceRaw, false);
    }

    public ModulePrefs(boolean replacePicker, boolean showButton, boolean forceRaw, boolean recordLogs) {
        this.replacePicker = replacePicker;
        this.showButton = showButton;
        this.forceRaw = forceRaw;
        this.recordLogs = recordLogs;
    }

    public static ModulePrefs defaults() {
        return new ModulePrefs(true, true, false, false);
    }

    public static ModulePrefs load() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(PKG, NAME);
            if (!prefs.getFile().canRead()) {
                return defaults();
            }
            prefs.reload();
            return from(prefs);
        } catch (Throwable t) {
            return defaults();
        }
    }

    public static ModulePrefs from(SharedPreferences prefs) {
        return new ModulePrefs(
                prefs.getBoolean(KEY_REPLACE, true),
                prefs.getBoolean(KEY_BUTTON, true),
                prefs.getBoolean(KEY_RAW, false),
                prefs.getBoolean(KEY_LOG, false)
        );
    }
}
