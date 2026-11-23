package io.github.Jackyhu32.wechat.picker;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory registry of recently imported video files so WeChat's {@code GalleryItem$MediaItem}
 * factory and {@link GalleryHandoff} can construct genuine {@code VideoMediaItem} instances
 * with real thumbnails and MIME types instead of falling back to {@code ImageMediaItem}.
 */
public final class VideoRegistry {
    private static final int MAX_ENTRIES = 64;
    private static final AtomicLong IDS = new AtomicLong(-1000L);
    private static final Map<String, VideoMeta> BY_PATH = new LinkedHashMap<>();

    public static final class VideoMeta {
        public final String path;
        public final String thumbPath;
        public final String mimeType;
        public final long durationMs;
        public final int width;
        public final int height;
        public final long syntheticId;

        public VideoMeta(
                String path,
                String thumbPath,
                String mimeType,
                long durationMs,
                int width,
                int height,
                long syntheticId
        ) {
            this.path = path;
            this.thumbPath = thumbPath;
            this.mimeType = (mimeType == null || mimeType.isEmpty()) ? "video/mp4" : mimeType;
            this.durationMs = Math.max(0L, durationMs);
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            this.syntheticId = syntheticId;
        }
    }

    private VideoRegistry() {
    }

    public static VideoMeta register(
            String path,
            String thumbPath,
            String mimeType,
            long durationMs,
            int width,
            int height
    ) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        VideoMeta meta = new VideoMeta(
                path,
                thumbPath,
                mimeType,
                durationMs,
                width,
                height,
                IDS.decrementAndGet()
        );
        synchronized (BY_PATH) {
            BY_PATH.remove(path);
            BY_PATH.put(path, meta);
            while (BY_PATH.size() > MAX_ENTRIES) {
                Iterator<String> it = BY_PATH.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                } else {
                    break;
                }
            }
        }
        return meta;
    }

    public static VideoMeta get(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        synchronized (BY_PATH) {
            return BY_PATH.get(path);
        }
    }

    static void clearForTest() {
        synchronized (BY_PATH) {
            BY_PATH.clear();
        }
    }
}
