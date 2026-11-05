package io.github.Jackyhu32.wechat.picker;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PickerClassMatcherTest {
    @Test
    public void albumPreviewIsTheStableTarget() {
        assertTrue(PickerClassMatcher.isPickerClass(
                "com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI",
                Collections.emptySet()));
    }

    @Test
    public void viewersAndCroppersAreNotTargets() {
        assertFalse(PickerClassMatcher.isPickerClass(
                "com.tencent.mm.plugin.gallery.ui.ImageGalleryUI",
                Collections.emptySet()));
        assertFalse(PickerClassMatcher.isPickerClass(
                "com.tencent.mm.plugin.gallery.ui.ImagePreviewUI",
                Collections.emptySet()));
        assertFalse(PickerClassMatcher.isPickerClass(
                "com.tencent.mm.ui.tools.CropImageNewUI",
                Collections.emptySet()));
    }

    @Test
    public void discoveredAlbumClassIsAcceptedAndChatClassIsNot() {
        String album = "com.tencent.mm.plugin.gallery.ui.MediaAlbumPickerUI";
        String chat = "com.tencent.mm.ui.chatting.ChattingUI";
        Set<String> discovered = Set.of(album, chat);
        assertTrue(PickerClassMatcher.isSafeDiscovered(album));
        assertFalse(PickerClassMatcher.isSafeDiscovered(chat));
        assertTrue(PickerClassMatcher.isPickerClass(album, discovered));
        assertFalse(PickerClassMatcher.isPickerClass(chat, discovered));
    }

    @Test
    public void imagePreviewStaysBlockedEvenIfDexKitReturnsIt() {
        String preview = "com.tencent.mm.plugin.gallery.ui.ImagePreviewUI";
        assertFalse(PickerClassMatcher.isSafeDiscovered(preview));
        assertFalse(PickerClassMatcher.isPickerClass(preview, Set.of(preview)));
    }
}
