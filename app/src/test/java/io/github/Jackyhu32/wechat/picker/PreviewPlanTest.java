package io.github.Jackyhu32.wechat.picker;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreviewPlanTest {
    @Test
    public void previewListIsImagesThenVideos() {
        PreviewPlan plan = PreviewPlan.from(
                Arrays.asList("/a.jpg", "/a.jpg", ""),
                Collections.singletonList("/v.mp4"));
        assertEquals(PreviewPlan.CLASS_NAME, "com.tencent.mm.plugin.gallery.ui.ImagePreviewUI");
        assertEquals(Arrays.asList("/a.jpg"), plan.images);
        assertEquals(Collections.singletonList("/v.mp4"), plan.videos);
        assertEquals(Arrays.asList("/a.jpg", "/v.mp4"), plan.previewPaths);
        assertEquals(0, plan.index);
        assertTrue(plan.hasVideos());
        assertFalse(plan.isPreviewPhoto);
    }

    @Test
    public void imagesOnlySetsIsPreviewPhotoTrue() {
        PreviewPlan plan = PreviewPlan.from(
                Collections.singletonList("/a.jpg"),
                Collections.emptyList());
        assertFalse(plan.hasVideos());
        assertTrue(plan.isPreviewPhoto);
    }
}
