package io.github.Jackyhu32.wechat.picker;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LaunchOptionsTest {
    @Test
    public void missingMaxBecomesNine() {
        LaunchOptions options = LaunchOptions.from(
                false, 0, false, 0, false, true, false, false, Collections.emptyMap());
        assertEquals(9, options.maxCount);
        assertEquals(LaunchOptions.BOTH, options.mediaType);
    }

    @Test
    public void singleSelectWithoutTypeIsImageOnly() {
        LaunchOptions options = LaunchOptions.from(
                true, 1, false, 0, false, true, false, false, Collections.emptyMap());
        assertEquals(1, options.maxCount);
        assertEquals(LaunchOptions.IMAGE, options.mediaType);
    }

    @Test
    public void explicitVideoTypeIsHonored() {
        LaunchOptions options = LaunchOptions.from(
                true, 1, true, LaunchOptions.VIDEO, false, true, false, false, Collections.emptyMap());
        assertEquals(LaunchOptions.VIDEO, options.mediaType);
    }

    @Test
    public void hugeMaxIsCapped() {
        LaunchOptions options = LaunchOptions.from(
                true, 9999, true, 3, false, true, false, false, Collections.emptyMap());
        assertEquals(100, options.maxCount);
    }

    @Test
    public void forceRawAndExplicitCompressOffBothCount() {
        LaunchOptions forced = LaunchOptions.from(
                true, 9, true, 1, false, true, false, true, Collections.emptyMap());
        LaunchOptions compressOff = LaunchOptions.from(
                true, 9, true, 1, true, false, false, false, Collections.emptyMap());
        assertTrue(forced.sendRaw);
        assertTrue(compressOff.sendRaw);
        LaunchOptions normal = LaunchOptions.from(
                true, 9, true, 1, true, true, false, false, Collections.emptyMap());
        assertFalse(normal.sendRaw);
    }
}
