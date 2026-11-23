package io.github.Jackyhu32.wechat.picker;

import java.util.Locale;

/**
 * Decides whether {@link GalleryHandoff} should return the picked media directly to the
 * calling activity ({@code setResult + finish}) or open {@code ImagePreviewUI}.
 *
 * <p>Chat / Moments multi-select wants {@code ImagePreviewUI} so the user can edit and
 * confirm before sending. Scan (掃一掃), OCR, Avatar, and single-select callers with a
 * calling activity expect the image path returned directly without jumping to chat preview.
 */
public final class DeliveryPolicy {
    public enum Mode {
        DIRECT_RESULT,
        IMAGE_PREVIEW
    }

    /**
     * Known WeChat {@code query_source_type} values for Scan / QR / Translate / OCR flows
     * where {@code AlbumPreviewUI} returns a single image directly to the caller.
     */
    private static final int[] SCAN_SOURCE_TYPES = {11, 14, 20, 21, 32, 34};

    private DeliveryPolicy() {
    }

    public static Mode decide(
            boolean hasCallingActivity,
            String callingClassName,
            int querySourceType,
            boolean hasScanExtra,
            boolean hasChatTarget,
            int maxSelectCount,
            boolean hasVideos
    ) {
        if (isScanContext(callingClassName, querySourceType, hasScanExtra)) {
            return Mode.DIRECT_RESULT;
        }
        if (hasCallingActivity && maxSelectCount <= 1 && !hasChatTarget && !hasVideos) {
            return Mode.DIRECT_RESULT;
        }
        return Mode.IMAGE_PREVIEW;
    }

    public static boolean isScanContext(String callingClassName, int querySourceType, boolean hasScanExtra) {
        if (hasScanExtra) {
            return true;
        }
        if (isScanClassName(callingClassName)) {
            return true;
        }
        for (int known : SCAN_SOURCE_TYPES) {
            if (querySourceType == known) {
                return true;
            }
        }
        return false;
    }

    static boolean isScanClassName(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        String lower = className.toLowerCase(Locale.US);
        return lower.contains("scan")
                || lower.contains("qbar")
                || lower.contains("ocr")
                || lower.contains("translate");
    }
}
