package io.github.Jackyhu32.wechat.picker;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogStoreTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void appendsReadsCountsAndClears() throws Exception {
        File file = new File(tmp.getRoot(), "diagnostics.log");
        assertEquals("", LogStore.readAll(file));
        assertEquals(0, LogStore.lineCount(""));

        LogStore.append(file, "12:00:01.000 line one");
        LogStore.append(file, "12:00:02.000 line two");

        String text = LogStore.readAll(file);
        assertTrue(text.contains("line one"));
        assertTrue(text.contains("line two"));
        assertEquals(2, LogStore.lineCount(text));

        LogStore.clear(file);
        assertEquals("", LogStore.readAll(file));
    }
}
