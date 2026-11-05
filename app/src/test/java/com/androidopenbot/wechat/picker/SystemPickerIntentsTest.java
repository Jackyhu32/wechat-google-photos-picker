package com.androidopenbot.wechat.picker;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemPickerIntentsTest {
    @Test
    public void hyperOsPhotoPickerIsKept() {
        assertTrue(SystemPickerIntents.keepResolvedPackage("com.android.photopicker"));
    }

    @Test
    public void otherPackagesAreNotForcedToHyper() {
        assertFalse(SystemPickerIntents.keepResolvedPackage("com.google.android.providers.media.module"));
        assertFalse(SystemPickerIntents.keepResolvedPackage("com.android.providers.media.module"));
        assertFalse(SystemPickerIntents.keepResolvedPackage(null));
    }
}
