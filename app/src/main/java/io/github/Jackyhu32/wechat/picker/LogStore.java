package io.github.Jackyhu32.wechat.picker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bounded UTF-8 log file store inside the module app's private files directory.
 * Automatically trims older lines when the file exceeds {@link #MAX_BYTES}.
 */
public final class LogStore {
    static final int MAX_BYTES = 512 * 1024;
    static final int TRIM_TO_BYTES = 384 * 1024;
    private static final Object FILE_LOCK = new Object();

    private LogStore() {
    }

    public static void append(File file, String line) {
        if (file == null || line == null || line.isEmpty()) {
            return;
        }
        synchronized (FILE_LOCK) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                }
                if (file.length() > MAX_BYTES) {
                    trim(file);
                }
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                    if (!line.endsWith("\n")) {
                        out.write('\n');
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static String readAll(File file) {
        if (file == null || !file.isFile()) {
            return "";
        }
        synchronized (FILE_LOCK) {
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[(int) Math.min(file.length(), MAX_BYTES + 4096)];
                int offset = 0;
                int n;
                while (offset < buf.length && (n = in.read(buf, offset, buf.length - offset)) >= 0) {
                    offset += n;
                }
                return new String(buf, 0, offset, StandardCharsets.UTF_8);
            } catch (Throwable ignored) {
                return "";
            }
        }
    }

    public static void clear(File file) {
        if (file == null) {
            return;
        }
        synchronized (FILE_LOCK) {
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    public static int lineCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        if (text.charAt(text.length() - 1) != '\n') {
            lines++;
        }
        return lines;
    }

    private static void trim(File file) throws IOException {
        byte[] all = readAll(file).getBytes(StandardCharsets.UTF_8);
        if (all.length <= TRIM_TO_BYTES) {
            return;
        }
        int start = all.length - TRIM_TO_BYTES;
        while (start < all.length && all[start] != '\n') {
            start++;
        }
        if (start < all.length) {
            start++;
        } else {
            start = all.length - TRIM_TO_BYTES;
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(all, start, all.length - start);
        }
    }
}
