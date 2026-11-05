package com.androidopenbot.wechat.picker;

import de.robv.android.xposed.XposedBridge;

/** LSPosed log line. Filter logcat or the LSPosed manager log for {@link #TAG}. */
public final class Logx {
    public static final String TAG = "WeChatSysPicker";

    private Logx() {
    }

    public static void i(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
