package com.androidopenbot.wechat.picker;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether an explicit component is WeChat's album picker.
 * The stable activity name is kept; DexKit may add renamed gallery classes.
 */
public final class PickerClassMatcher {
    private PickerClassMatcher() {
    }

    public static boolean isPickerClass(String className, Set<String> discovered) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        if (!className.contains("tencent.mm") && !className.startsWith(".")) {
            return false;
        }
        String simple = simpleName(className);
        if ("AlbumPreviewUI".equals(simple) || simple.endsWith("AlbumPreviewUI")) {
            return true;
        }
        return discovered != null && discovered.contains(className) && isSafeDiscovered(className);
    }

    /**
     * DexKit hits must still look like the gallery album, not the image viewer,
     * cropper, camera, or a chat screen that merely mentions the same extras.
     */
    public static boolean isSafeDiscovered(String className) {
        if (className == null) {
            return false;
        }
        String lower = className.toLowerCase(Locale.US);
        if (!lower.contains("tencent.mm") || !lower.contains("gallery")) {
            return false;
        }
        if (lower.contains("imagegallery")
                || lower.contains("imagepreview")
                || lower.contains("crop")
                || lower.contains("record")
                || lower.contains("sight")
                || lower.contains("finder")) {
            return false;
        }
        return lower.contains("album") || lower.contains("picker");
    }

    static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }
}
