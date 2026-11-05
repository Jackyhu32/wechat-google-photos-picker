package com.androidopenbot.wechat.picker;

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
    }

    @Test
    public void sendLabelsMatchTheStockButton() {
        assertTrue(GalleryHandoff.looksLikeSendLabel("发送"));
        assertTrue(GalleryHandoff.looksLikeSendLabel("发送(1)"));
        assertTrue(GalleryHandoff.looksLikeSendLabel("傳送"));
        assertTrue(GalleryHandoff.looksLikeSendLabel("完成"));
        assertTrue(GalleryHandoff.looksLikeSendLabel("Send"));
        assertFalse(GalleryHandoff.looksLikeSendLabel("预览"));
        assertFalse(GalleryHandoff.looksLikeSendLabel(null));
        assertFalse(GalleryHandoff.looksLikeSendLabel("  "));
    }
}
