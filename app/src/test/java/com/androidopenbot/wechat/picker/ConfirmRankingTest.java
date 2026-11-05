package com.androidopenbot.wechat.picker;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConfirmRankingTest {
    @Test
    public void zeroArgAlbumConfirmBeatsLifecycleAndPreview() {
        ConfirmRanking.Target confirm = new ConfirmRanking.Target(
                "com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI", "a", 0, "void", true);
        ConfirmRanking.Target lifecycle = new ConfirmRanking.Target(
                "com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI", "onActivityResult", 3, "void", true);
        ConfirmRanking.Target preview = new ConfirmRanking.Target(
                "com.tencent.mm.plugin.gallery.ui.ImagePreviewUI", "b", 0, "void", true);
        ConfirmRanking.Target ignored = new ConfirmRanking.Target(
                "com.tencent.mm.ui.chatting.ChattingUI", "send", 0, "void", true);

        assertTrue(ConfirmRanking.score(lifecycle) < 0);
        assertTrue(ConfirmRanking.score(ignored) < 0);
        assertTrue(ConfirmRanking.score(confirm) > ConfirmRanking.score(preview));
        assertEquals(confirm, ConfirmRanking.best(Arrays.asList(lifecycle, preview, confirm, ignored)));
    }

    @Test
    public void missingCropListIsNotAConfirm() {
        ConfirmRanking.Target target = new ConfirmRanking.Target(
                "com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI", "a", 0, "void", false);
        assertTrue(ConfirmRanking.score(target) < 0);
        assertNull(ConfirmRanking.best(Arrays.asList(target)));
    }
}
