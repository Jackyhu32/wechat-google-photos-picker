package io.github.Jackyhu32.wechat.picker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the original album Intent asked for.
 * {@code query_source_type} is a business source (chat, moments, …), not a mime filter,
 * so it is intentionally not read here.
 */
public final class LaunchOptions {
    public static final int IMAGE = 1;
    public static final int VIDEO = 2;
    public static final int BOTH = 3;

    public final int maxCount;
    public final int mediaType;
    public final boolean sendRaw;
    public final Map<String, String> passthrough;

    public LaunchOptions(int maxCount, int mediaType, boolean sendRaw, Map<String, String> passthrough) {
        this.maxCount = maxCount;
        this.mediaType = mediaType;
        this.sendRaw = sendRaw;
        this.passthrough = passthrough;
    }

    public static LaunchOptions from(
            boolean hasMax,
            int maxRaw,
            boolean hasMedia,
            int mediaRaw,
            boolean hasCompress,
            boolean compress,
            boolean sendRawExtra,
            boolean forceRaw,
            Map<String, String> passthrough
    ) {
        int max = (!hasMax || maxRaw <= 0) ? 9 : Math.min(maxRaw, 100);
        int media = normalizeMedia(hasMedia, mediaRaw, max);
        boolean sendRaw = forceRaw || sendRawExtra || (hasCompress && !compress);
        Map<String, String> copy = new LinkedHashMap<>();
        if (passthrough != null) {
            copy.putAll(passthrough);
        }
        return new LaunchOptions(max, media, sendRaw, Collections.unmodifiableMap(copy));
    }

    static int normalizeMedia(boolean hasMedia, int mediaRaw, int max) {
        if (hasMedia) {
            if (mediaRaw == IMAGE || mediaRaw == VIDEO || mediaRaw == BOTH) {
                return mediaRaw;
            }
            // Older builds omit the extra or pass 0 for "images".
            if (mediaRaw == 0) {
                return IMAGE;
            }
            return BOTH;
        }
        // A single-select launch without a type is the avatar / crop path.
        if (max <= 1) {
            return IMAGE;
        }
        return BOTH;
    }
}
