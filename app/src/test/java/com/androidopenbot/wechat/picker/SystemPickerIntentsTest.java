package com.androidopenbot.wechat.picker;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SystemPickerIntentsTest {
    @Test
    public void primaryPickerIsGooglePhotosExternalPicker() {
        assertEquals("android.intent.action.GET_CONTENT", SystemPickerIntents.PRIMARY_ACTION);
        assertEquals("com.google.android.apps.photos", SystemPickerIntents.PHOTOS_PACKAGE);
        assertEquals(
                "com.google.android.apps.photos.picker.external.ExternalPickerActivity",
                SystemPickerIntents.PHOTOS_ACTIVITY);
        assertFalse(SystemPickerIntents.PRIMARY_ACTION.equals(SystemPickerIntents.HYPER_ACTION));
        assertEquals("android.provider.action.PICK_IMAGES", SystemPickerIntents.HYPER_ACTION);
    }

    @Test
    public void mimeTypeFollowsQueryMediaType() {
        assertEquals("image/*", SystemPickerIntents.mimeType(LaunchOptions.IMAGE));
        assertEquals("video/*", SystemPickerIntents.mimeType(LaunchOptions.VIDEO));
        assertEquals("*/*", SystemPickerIntents.mimeType(LaunchOptions.BOTH));
        assertNull(SystemPickerIntents.extraMimeTypes(LaunchOptions.IMAGE));
        assertNull(SystemPickerIntents.extraMimeTypes(LaunchOptions.VIDEO));
        assertArrayEquals(
                new String[]{"image/*", "video/*"},
                SystemPickerIntents.extraMimeTypes(LaunchOptions.BOTH));
    }

    @Test
    public void allowMultipleOnlyWhenMaxAboveOne() {
        LaunchOptions multi = LaunchOptions.from(
                true, 9, true, LaunchOptions.IMAGE, false, true, false, false, Collections.emptyMap());
        LaunchOptions single = LaunchOptions.from(
                true, 1, true, LaunchOptions.IMAGE, false, true, false, false, Collections.emptyMap());
        LaunchOptions video = LaunchOptions.from(
                true, 1, true, LaunchOptions.VIDEO, false, true, false, false, Collections.emptyMap());
        assertTrue(SystemPickerIntents.allowMultiple(multi));
        assertFalse(SystemPickerIntents.allowMultiple(single));
        assertTrue(SystemPickerIntents.allowMultiple(null));
        assertEquals(LaunchOptions.IMAGE, SystemPickerIntents.mediaType(single));
        assertEquals(LaunchOptions.VIDEO, SystemPickerIntents.mediaType(video));
        assertEquals(LaunchOptions.BOTH, SystemPickerIntents.mediaType(null));
        assertEquals("image/*", SystemPickerIntents.mimeType(SystemPickerIntents.mediaType(single)));
        assertEquals("video/*", SystemPickerIntents.mimeType(SystemPickerIntents.mediaType(video)));
    }

    @Test
    public void hyperFallbackStillPrefersHyperOsPhotoPicker() {
        assertTrue(SystemPickerIntents.keepResolvedPackage("com.android.photopicker"));
        assertFalse(SystemPickerIntents.keepResolvedPackage(SystemPickerIntents.PHOTOS_PACKAGE));
    }

    @Test
    public void otherPackagesAreNotForcedToHyper() {
        assertFalse(SystemPickerIntents.keepResolvedPackage("com.google.android.providers.media.module"));
        assertFalse(SystemPickerIntents.keepResolvedPackage("com.android.providers.media.module"));
        assertFalse(SystemPickerIntents.keepResolvedPackage(null));
    }
}
