package io.github.Jackyhu32.wechat.picker;

import android.content.Intent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LaunchOptionsReader {
    private LaunchOptionsReader() {
    }

    public static LaunchOptions fromIntent(Intent intent, boolean forceRaw) {
        Map<String, String> pass = new LinkedHashMap<>();
        if (intent != null) {
            for (String key : WeChatResultContract.PASSTHROUGH) {
                String value = intent.getStringExtra(key);
                if (value != null && !value.isEmpty()) {
                    pass.put(key, value);
                }
            }
        }
        boolean hasMax = intent != null && intent.hasExtra(WeChatResultContract.MAX_SELECT);
        int max = intent == null ? 9 : intent.getIntExtra(WeChatResultContract.MAX_SELECT, 9);
        boolean hasMedia = intent != null && intent.hasExtra(WeChatResultContract.QUERY_MEDIA);
        int media = intent == null ? 0 : intent.getIntExtra(WeChatResultContract.QUERY_MEDIA, 0);
        boolean hasCompress = intent != null && intent.hasExtra(WeChatResultContract.COMPRESS);
        boolean compress = intent == null || intent.getBooleanExtra(WeChatResultContract.COMPRESS, true);
        boolean sendRaw = intent != null && intent.getBooleanExtra(WeChatResultContract.SEND_RAW, false);
        return LaunchOptions.from(hasMax, max, hasMedia, media, hasCompress, compress, sendRaw, forceRaw, pass);
    }
}
