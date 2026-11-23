package io.github.Jackyhu32.wechat.picker;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GalleryCoreBridgeTest {
    public static final class FakeGalleryCore {
        static ArrayList<Object> previewList;
        static final LinkedHashSet<Object> SELECTED_SET = new LinkedHashSet<>();

        public static void p(ArrayList<Object> list) {
            previewList = list;
        }

        public static LinkedHashSet<Object> m() {
            return SELECTED_SET;
        }
    }

    @Before
    public void reset() {
        FakeGalleryCore.previewList = null;
        FakeGalleryCore.SELECTED_SET.clear();
    }

    @Test
    public void seedsBothStaticPreviewListAndSelectedSet() {
        boolean ok = GalleryCoreBridge.seedClass(FakeGalleryCore.class, Arrays.asList("video1", "video2"));
        assertTrue(ok);
        assertEquals(Arrays.asList("video1", "video2"), FakeGalleryCore.previewList);
        assertEquals(2, FakeGalleryCore.SELECTED_SET.size());
        assertTrue(FakeGalleryCore.SELECTED_SET.contains("video1"));
        assertTrue(FakeGalleryCore.SELECTED_SET.contains("video2"));
    }
}
