package io.github.Jackyhu32.wechat.picker;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * Pure-Java helper for rewriting WeChat {@code GalleryItem$MediaItem} factory arguments
 * and filling metadata fields on {@code VideoMediaItem} instances.
 */
public final class VideoMediaItemPatch {
    private VideoMediaItemPatch() {
    }

    public static boolean isFactorySignature(Class<?>[] params) {
        return params != null
                && params.length == 5
                && params[0] == int.class
                && params[1] == long.class
                && params[2] == String.class
                && params[3] == String.class
                && params[4] == String.class;
    }

    public static VideoRegistry.VideoMeta rewriteFactoryArgs(Object[] args) {
        if (args == null || args.length < 5 || !(args[2] instanceof String)) {
            return null;
        }
        String origPath = (String) args[2];
        VideoRegistry.VideoMeta meta = VideoRegistry.get(origPath);
        if (meta == null) {
            return null;
        }
        if (args[0] instanceof Integer && ((Integer) args[0]) != 2) {
            args[0] = 2;
        }
        if (args[1] instanceof Long && ((Long) args[1]) == 0L) {
            args[1] = meta.syntheticId;
        }
        String thumb = args[3] instanceof String ? (String) args[3] : null;
        if (meta.thumbPath != null && !meta.thumbPath.isEmpty()
                && (thumb == null || thumb.isEmpty() || thumb.equals(origPath))) {
            args[3] = meta.thumbPath;
        }
        String mime = args[4] instanceof String ? (String) args[4] : null;
        if (mime == null || mime.isEmpty() || mime.toLowerCase(Locale.US).startsWith("image/")) {
            args[4] = meta.mimeType;
        }
        return meta;
    }

    public static VideoRegistry.VideoMeta findRegisteredVideo(Object[] args) {
        if (args == null || args.length < 3 || !(args[2] instanceof String)) {
            return null;
        }
        return VideoRegistry.get((String) args[2]);
    }

    /**
     * Populates video metadata fields (both named fields and WeChat's {@code GalleryItem$VideoMediaItem}
     * fields {@code x/y/z/A/B}) when currently unset (0, -1, or null/empty).
     */
    public static void populateVideoItemFields(Object item, VideoRegistry.VideoMeta meta) {
        if (item == null || meta == null) {
            return;
        }
        Class<?> itemClass = item.getClass();
        boolean isWeChatVideoItem = itemClass.getName().endsWith("VideoMediaItem");
        if (isWeChatVideoItem) {
            populateWeChatVideoFields(item, itemClass, meta);
        }

        Class<?> type = itemClass;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String name = field.getName().toLowerCase(Locale.US);
                try {
                    if (field.getType() == int.class) {
                        field.setAccessible(true);
                        int current = field.getInt(item);
                        if (current > 0) {
                            continue;
                        }
                        if (name.contains("duration") && meta.durationMs > 0) {
                            field.setInt(item, (int) Math.min(meta.durationMs, Integer.MAX_VALUE));
                        } else if (name.contains("width") && meta.width > 0) {
                            field.setInt(item, meta.width);
                        } else if (name.contains("height") && meta.height > 0) {
                            field.setInt(item, meta.height);
                        }
                    } else if (field.getType() == long.class && name.contains("duration") && meta.durationMs > 0) {
                        field.setAccessible(true);
                        if (field.getLong(item) <= 0L) {
                            field.setLong(item, meta.durationMs);
                        }
                    } else if (field.getType() == String.class && name.contains("mime")) {
                        field.setAccessible(true);
                        Object current = field.get(item);
                        if (current == null || String.valueOf(current).isEmpty()) {
                            String val = (name.contains("audio") && meta.audioTrackMime != null)
                                    ? meta.audioTrackMime
                                    : (meta.videoTrackMime != null ? meta.videoTrackMime : meta.mimeType);
                            field.set(item, val);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    /**
     * In WeChat 8.0.x {@code GalleryItem$VideoMediaItem}:
     * {@code x} = videoTrackMime, {@code y} = audioTrackMime,
     * {@code z} = durationMs, {@code A} = videoHeight, {@code B} = videoWidth.
     */
    private static void populateWeChatVideoFields(Object item, Class<?> itemClass, VideoRegistry.VideoMeta meta) {
        if (meta.videoTrackMime != null && !meta.videoTrackMime.isEmpty()) {
            setStringIfEmpty(item, itemClass, "x", meta.videoTrackMime);
        }
        if (meta.audioTrackMime != null && !meta.audioTrackMime.isEmpty()) {
            setStringIfEmpty(item, itemClass, "y", meta.audioTrackMime);
        }
        if (meta.durationMs > 0) {
            setIntIfUnset(item, itemClass, "z", (int) Math.min(meta.durationMs, Integer.MAX_VALUE));
        }
        if (meta.height > 0) {
            setIntIfUnset(item, itemClass, "A", meta.height);
        }
        if (meta.width > 0) {
            setIntIfUnset(item, itemClass, "B", meta.width);
        }
    }

    private static void setStringIfEmpty(Object item, Class<?> cls, String fieldName, String value) {
        try {
            Field f = cls.getDeclaredField(fieldName);
            if (f.getType() != String.class || Modifier.isStatic(f.getModifiers())) {
                return;
            }
            f.setAccessible(true);
            Object cur = f.get(item);
            if (cur == null || String.valueOf(cur).isEmpty()) {
                f.set(item, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setIntIfUnset(Object item, Class<?> cls, String fieldName, int value) {
        try {
            Field f = cls.getDeclaredField(fieldName);
            if (f.getType() != int.class || Modifier.isStatic(f.getModifiers())) {
                return;
            }
            f.setAccessible(true);
            if (f.getInt(item) <= 0) {
                f.setInt(item, value);
            }
        } catch (Throwable ignored) {
        }
    }
}
