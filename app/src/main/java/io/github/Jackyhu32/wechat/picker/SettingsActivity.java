package io.github.Jackyhu32.wechat.picker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Writes {@code config.xml} with {@link Context#MODE_WORLD_READABLE}. LSPosed (min version 93)
 * redirects that mode into the directory {@code XSharedPreferences} can read from WeChat.
 */
public class SettingsActivity extends Activity {
    private boolean worldReadable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        SharedPreferences prefs = openPrefs();
        TextView version = findViewById(R.id.version_text);
        version.setText(versionLabel());
        TextView banner = findViewById(R.id.status_banner);
        banner.setText(worldReadable ? R.string.status_ready : R.string.status_private);

        Switch replace = findViewById(R.id.switch_replace);
        Switch button = findViewById(R.id.switch_button);
        Switch raw = findViewById(R.id.switch_raw);
        replace.setChecked(prefs.getBoolean(ModulePrefs.KEY_REPLACE, true));
        button.setChecked(prefs.getBoolean(ModulePrefs.KEY_BUTTON, true));
        raw.setChecked(prefs.getBoolean(ModulePrefs.KEY_RAW, false));
        replace.setOnCheckedChangeListener(saver(prefs, ModulePrefs.KEY_REPLACE));
        button.setOnCheckedChangeListener(saver(prefs, ModulePrefs.KEY_BUTTON));
        raw.setOnCheckedChangeListener(saver(prefs, ModulePrefs.KEY_RAW));
    }

    private CompoundButton.OnCheckedChangeListener saver(SharedPreferences prefs, String key) {
        return (button, checked) -> prefs.edit().putBoolean(key, checked).apply();
    }

    @SuppressLint("WorldReadableFiles")
    @SuppressWarnings("deprecation")
    private SharedPreferences openPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences(ModulePrefs.NAME, Context.MODE_WORLD_READABLE);
            worldReadable = true;
            return prefs;
        } catch (SecurityException ignored) {
            worldReadable = false;
            return getSharedPreferences(ModulePrefs.NAME, Context.MODE_PRIVATE);
        }
    }

    private String versionLabel() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return "版本 " + info.versionName + "  ·  com.tencent.mm";
        } catch (Throwable t) {
            return "com.tencent.mm";
        }
    }
}
