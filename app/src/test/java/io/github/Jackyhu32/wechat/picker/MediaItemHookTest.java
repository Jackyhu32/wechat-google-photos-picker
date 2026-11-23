package io.github.Jackyhu32.wechat.picker;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MediaItemHookTest {
    @After
    public void tearDown() {
        VideoRegistry.clearForTest();
    }

    @Test
    public void rewritesFallbackImageArgsToVideoForRegisteredVideo() {
        String videoPath = "/storage/emulated/0/Android/data/com.tencent.mm/files/syspicker/VID_1.mp4";
        String thumbPath = "/storage/emulated/0/Android/data/com.tencent.mm/files/syspicker/THUMB_1.jpg";
        VideoRegistry.VideoMeta meta = VideoRegistry.register(videoPath, thumbPath, "video/mp4", 12500L, 1080, 1920);
        assertNotNull(meta);

        Object[] args = new Object[]{1, 0L, videoPath, videoPath, ""};
        VideoRegistry.VideoMeta matched = VideoMediaItemPatch.rewriteFactoryArgs(args);

        assertNotNull(matched);
        assertEquals(2, args[0]);
        assertTrue(((Long) args[1]) < 0L);
        assertEquals(videoPath, args[2]);
        assertEquals(thumbPath, args[3]);
        assertEquals("video/mp4", args[4]);
    }

    @Test
    public void ignoresUnregisteredImagePaths() {
        Object[] args = new Object[]{1, 0L, "/storage/emulated/0/DCIM/IMG_1.jpg", "/storage/emulated/0/DCIM/IMG_1.jpg", "image/jpeg"};
        assertNull(VideoMediaItemPatch.rewriteFactoryArgs(args));
        assertEquals(1, args[0]);
        assertEquals("image/jpeg", args[4]);
    }

    static final class FakeVideoMediaItem {
        int videoDuration;
        int videoWidth;
        int videoHeight;
        String videoMime;
    }

    @Test
    public void populatesNamedVideoMetadataFieldsWhenZero() {
        VideoRegistry.VideoMeta meta = VideoRegistry.register("/v.mp4", "/t.jpg", "video/mp4", 8400L, 720, 1280);
        FakeVideoMediaItem item = new FakeVideoMediaItem();
        VideoMediaItemPatch.populateVideoItemFields(item, meta);

        assertEquals(8400, item.videoDuration);
        assertEquals(720, item.videoWidth);
        assertEquals(1280, item.videoHeight);
        assertEquals("video/mp4", item.videoMime);
    }
}
