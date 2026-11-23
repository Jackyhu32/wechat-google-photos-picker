package io.github.Jackyhu32.wechat.picker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Writes {@code config.xml} with {@link Context#MODE_WORLD_READABLE}. LSPosed (min version 93)
 * redirects that mode into the directory {@code XSharedPreferences} can read from WeChat.
 */
public class SettingsActivity extends Activity {
    private boolean worldReadable;
    private TextView logStatus;
    private TextView logPreview;

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
        Switch logSwitch = findViewById(R.id.switch_log);
        replace.setChecked(prefs.getBoolean(ModulePrefs.KEY_REPLACE, true));
        button.setChecked(prefs.getBoolean(ModulePrefs.KEY_BUTTON, true));
        raw.setChecked(prefs.getBoolean(ModulePrefs.KEY_RAW, false));
        logSwitch.setChecked(prefs.getBoolean(ModulePrefs.KEY_LOG, false));
        replace.setOnCheckedChangeListener(saver(prefs, ModulePrefs.KEY_REPLACE));
        button.setOnCheckedChangeListener(saver(prefs, ModulePrefs.KEY_BUTTON));
        raw.setOnCheckedChangeListener(saver(prefs, ModulePrefs.KEY_RAW));
        logSwitch.setOnCheckedChangeListener((view, checked) -> {
            prefs.edit().putBoolean(ModulePrefs.KEY_LOG, checked).apply();
            if (checked) {
                LogStore.append(LogCollectorProvider.logFile(this), "=== 診斷日誌已開啟 (" + versionLabel() + ") ===");
            }
            refreshLogUi();
        });

        logStatus = findViewById(R.id.log_status);
        logPreview = findViewById(R.id.log_preview);
        Button copyBtn = findViewById(R.id.btn_copy_log);
        Button shareBtn = findViewById(R.id.btn_share_log);
        Button clearBtn = findViewById(R.id.btn_clear_log);

        copyBtn.setOnClickListener(v -> copyLogs());
        shareBtn.setOnClickListener(v -> shareLogs());
        clearBtn.setOnClickListener(v -> {
            LogStore.clear(LogCollectorProvider.logFile(this));
            refreshLogUi();
            Toast.makeText(this, "已清除日誌", Toast.LENGTH_SHORT).show();
        });
        refreshLogUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLogUi();
    }

    private void refreshLogUi() {
        if (logStatus == null || logPreview == null) {
            return;
        }
        String content = LogStore.readAll(LogCollectorProvider.logFile(this));
        int lines = LogStore.lineCount(content);
        logStatus.setText("已記錄 " + lines + " 行");
        if (content.isEmpty()) {
            logPreview.setText(R.string.log_empty);
        } else {
            logPreview.setText(tailLines(content, 14));
        }
    }

    private void copyLogs() {
        String content = LogStore.readAll(LogCollectorProvider.logFile(this));
        if (content.isEmpty()) {
            Toast.makeText(this, "目前沒有可複製的日誌", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("WeChatSysPicker Logs", content));
            Toast.makeText(this, "已複製 " + LogStore.lineCount(content) + " 行日誌", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareLogs() {
        String content = LogStore.readAll(LogCollectorProvider.logFile(this));
        if (content.isEmpty()) {
            Toast.makeText(this, "目前沒有可分享的日誌", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "WeChatSysPicker 診斷日誌");
        share.putExtra(Intent.EXTRA_TEXT, content);
        startActivity(Intent.createChooser(share, "分享診斷日誌"));
    }

    private static String tailLines(String text, int maxLines) {
        String[] parts = text.split("\n");
        if (parts.length <= maxLines) {
            return text.trim();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = parts.length - maxLines; i < parts.length; i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
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
