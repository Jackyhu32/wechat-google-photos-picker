package io.github.Jackyhu32.wechat.picker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What ImagePreviewUI needs so the stock send button sees the imported files. */
public final class PreviewPlan {
    public static final String CLASS_NAME = "com.tencent.mm.plugin.gallery.ui.ImagePreviewUI";

    public final List<String> images;
    public final List<String> videos;
    public final List<String> previewPaths;
    public final int index;

    public PreviewPlan(List<String> images, List<String> videos, List<String> previewPaths, int index) {
        this.images = images;
        this.videos = videos;
        this.previewPaths = previewPaths;
        this.index = index;
    }

    public static PreviewPlan from(List<String> images, List<String> videos) {
        List<String> imageCopy = copy(images);
        List<String> videoCopy = copy(videos);
        List<String> preview = new ArrayList<>();
        preview.addAll(imageCopy);
        preview.addAll(videoCopy);
        return new PreviewPlan(imageCopy, videoCopy, Collections.unmodifiableList(preview), 0);
    }

    private static List<String> copy(List<String> paths) {
        List<String> out = new ArrayList<>();
        if (paths == null) {
            return out;
        }
        for (String path : paths) {
            if (path != null && !path.isEmpty() && !out.contains(path)) {
                out.add(path);
            }
        }
        return out;
    }
}
