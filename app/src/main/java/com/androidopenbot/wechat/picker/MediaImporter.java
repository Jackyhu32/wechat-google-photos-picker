package com.androidopenbot.wechat.picker;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.system.Os;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns picker content URIs into files under WeChat's own external-files directory.
 * Shared-storage files are reused in place. Cloud items are streams and have to be copied.
 */
public final class MediaImporter {
    private static final long MAX_BYTES = 1536L * 1024L * 1024L;
    private static final long MD5_LIMIT = 512L * 1024L * 1024L;
    private static final String[] VIDEO_EXT = {
            "mp4", "mov", "m4v", "3gp", "3gpp", "webm", "mkv", "avi"
    };

    private MediaImporter() {
    }

    public static final class Imported {
        public final boolean video;
        public final String path;
        public final String md5;

        Imported(boolean video, String path, String md5) {
            this.video = video;
            this.path = path;
            this.md5 = md5;
        }
    }

    public static List<Imported> importAll(Context context, Intent data) {
        List<Imported> out = new ArrayList<>();
        if (context == null || data == null) {
            return out;
        }
        List<Uri> uris = extractUris(data);
        Logx.i("picker returned " + uris.size() + " uri(s)");
        if (uris.isEmpty()) {
            return out;
        }
        File dir = outputDir(context);
        cleanupOld(dir);
        ContentResolver resolver = context.getContentResolver();
        int index = 0;
        for (Uri uri : uris) {
            index++;
            try {
                Imported imported = importOne(resolver, dir, uri, index);
                if (imported != null) {
                    out.add(imported);
                }
            } catch (Throwable t) {
                Logx.i("import failed for item " + index + ": " + t);
            }
        }
        return out;
    }

    public static String videoThumb(Context context, String videoPath) {
        if (context == null || videoPath == null) {
            return null;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap frame = null;
        try {
            retriever.setDataSource(videoPath);
            frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                return null;
            }
            File dir = outputDir(context);
            File thumb = new File(dir, "THUMB_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(thumb)) {
                if (!frame.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
                    return null;
                }
            }
            Logx.i("video thumb " + thumb.getName() + " " + thumb.length() + " bytes");
            return thumb.getAbsolutePath();
        } catch (Throwable t) {
            Logx.i("video thumb failed: " + t);
            return null;
        } finally {
            if (frame != null) {
                frame.recycle();
            }
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
    }

    public static String md5(String path) {
        File file = new File(path);
        if (!file.isFile() || file.length() <= 0 || file.length() > MD5_LIMIT) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            return digest(in);
        } catch (Throwable t) {
            Logx.i("md5 failed: " + t);
            return null;
        }
    }

    private static Imported importOne(ContentResolver resolver, File dir, Uri uri, int index) throws Exception {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
            // Photo picker grants are usually temporary. The copy below still works.
        }
        String mime = null;
        try {
            mime = resolver.getType(uri);
        } catch (Throwable ignored) {
        }
        long reported = querySize(resolver, uri);
        if (reported > MAX_BYTES) {
            Logx.i("skip item " + index + ", size " + reported);
            return null;
        }
        String shared = sharedStoragePath(resolver, uri);
        String ext = extension(mime, queryDisplayName(resolver, uri), shared);
        boolean video = isVideo(mime, ext);
        if (shared != null) {
            Logx.i("reuse shared file item " + index + " video=" + video + " " + shared);
            return new Imported(video, shared, null);
        }
        String suffix = ext.isEmpty() ? "" : "." + ext;
        String prefix = video ? "VID_" : "IMG_";
        File out = new File(dir, prefix + System.currentTimeMillis() + "_" + index + suffix);
        MessageDigest digest = video ? MessageDigest.getInstance("MD5") : null;
        try (InputStream raw = resolver.openInputStream(uri)) {
            if (raw == null) {
                Logx.i("null stream for item " + index);
                return null;
            }
            try (OutputStream dest = new FileOutputStream(out)) {
                byte[] buf = new byte[256 * 1024];
                long total = 0;
                int n;
                while ((n = raw.read(buf)) >= 0) {
                    total += n;
                    if (total > MAX_BYTES) {
                        dest.close();
                        //noinspection ResultOfMethodCallIgnored
                        out.delete();
                        Logx.i("skip item " + index + ", copy exceeded cap");
                        return null;
                    }
                    dest.write(buf, 0, n);
                    if (digest != null) {
                        digest.update(buf, 0, n);
                    }
                }
            }
        }
        if (!out.isFile() || out.length() <= 0) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return null;
        }
        if (ext.isEmpty()) {
            String sniffed = sniff(out);
            if (!sniffed.isEmpty()) {
                video = isVideo(null, sniffed);
                File renamed = new File(dir, out.getName() + "." + sniffed);
                if (out.renameTo(renamed)) {
                    out = renamed;
                }
                if ("heic".equals(sniffed) || "heif".equals(sniffed)) {
                    ext = sniffed;
                }
            }
        }
        if ("heic".equals(ext) || "heif".equals(ext) || (mime != null && (mime.contains("heic") || mime.contains("heif")))) {
            File jpeg = transcodeHeic(out, dir, index);
            if (jpeg != null) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                out = jpeg;
                video = false;
            }
        }
        String hash = digest == null ? null : hex(digest.digest());
        Logx.i("copied item " + index + " video=" + video + " bytes=" + out.length() + " -> " + out.getAbsolutePath());
        return new Imported(video, out.getAbsolutePath(), hash);
    }

    private static List<Uri> extractUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                addUri(uris, uri);
            }
        }
        addUri(uris, data.getData());
        return uris;
    }

    private static void addUri(List<Uri> uris, Uri uri) {
        if (uri != null && !uris.contains(uri)) {
            uris.add(uri);
        }
    }

    private static File outputDir(Context context) {
        File dir = context.getExternalFilesDir("syspicker");
        if (dir == null) {
            dir = new File(context.getCacheDir(), "syspicker");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            Logx.i("mkdir failed " + dir);
        }
        return dir;
    }

    private static void cleanupOld(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L;
        for (File file : files) {
            if (file.lastModified() < cutoff && !file.delete()) {
                Logx.i("could not delete old " + file.getName());
            }
        }
    }

    private static String sharedStoragePath(ContentResolver resolver, Uri uri) {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = resolver.openFileDescriptor(uri, "r");
            if (pfd == null) {
                return null;
            }
            String link = Os.readlink("/proc/self/fd/" + pfd.getFd());
            if (link == null) {
                return null;
            }
            boolean shared = link.startsWith("/storage/")
                    || link.startsWith("/sdcard/")
                    || link.contains("/emulated/");
            if (!shared) {
                return null;
            }
            File file = new File(link);
            if (file.isFile() && file.canRead() && file.length() > 0 && file.length() <= MAX_BYTES) {
                return file.getAbsolutePath();
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static long querySize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String extension(String mime, String displayName, String sharedPath) {
        String fromMime = null;
        if (mime != null) {
            fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (fromMime == null && mime.startsWith("image/")) {
                fromMime = mime.substring("image/".length());
            } else if (fromMime == null && mime.startsWith("video/")) {
                fromMime = mime.substring("video/".length());
            }
        }
        String ext = sanitizeExt(fromMime);
        if (!ext.isEmpty()) {
            return canonicalExt(ext);
        }
        ext = extOfName(displayName);
        if (!ext.isEmpty()) {
            return canonicalExt(ext);
        }
        return canonicalExt(extOfName(sharedPath));
    }

    private static String extOfName(String name) {
        if (name == null) {
            return "";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return "";
        }
        return sanitizeExt(base.substring(dot + 1));
    }

    private static String sanitizeExt(String ext) {
        if (ext == null) {
            return "";
        }
        String cleaned = ext.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
        if (cleaned.length() > 5) {
            return "";
        }
        return cleaned;
    }

    private static String canonicalExt(String ext) {
        if ("jpeg".equals(ext) || "jpg".equals(ext)) {
            return "jpg";
        }
        if ("quicktime".equals(ext)) {
            return "mov";
        }
        return ext == null ? "" : ext;
    }

    private static boolean isVideo(String mime, String ext) {
        if (mime != null && mime.toLowerCase(Locale.US).startsWith("video/")) {
            return true;
        }
        if (ext == null) {
            return false;
        }
        for (String candidate : VIDEO_EXT) {
            if (candidate.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    private static String sniff(File file) {
        byte[] buf = new byte[16];
        int n;
        try (InputStream in = new FileInputStream(file)) {
            n = in.read(buf);
        } catch (Throwable t) {
            return "";
        }
        if (n >= 3 && (buf[0] & 0xFF) == 0xFF && (buf[1] & 0xFF) == 0xD8) {
            return "jpg";
        }
        if (n >= 8 && (buf[0] & 0xFF) == 0x89 && buf[1] == 0x50 && buf[2] == 0x4E && buf[3] == 0x47) {
            return "png";
        }
        if (n >= 6 && buf[0] == 'G' && buf[1] == 'I' && buf[2] == 'F') {
            return "gif";
        }
        if (n >= 12 && buf[0] == 'R' && buf[1] == 'I' && buf[2] == 'F' && buf[3] == 'F') {
            return "webp";
        }
        if (n >= 12 && buf[4] == 'f' && buf[5] == 't' && buf[6] == 'y' && buf[7] == 'p') {
            String brand = new String(buf, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.US);
            if (brand.startsWith("hei") || brand.contains("mif")) {
                return "heic";
            }
            if (brand.startsWith("qt")) {
                return "mov";
            }
            if (brand.startsWith("3gp")) {
                return "3gp";
            }
            return "mp4";
        }
        return "";
    }

    private static File transcodeHeic(File source, File dir, int index) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        int sample = 1;
        int edge = Math.max(bounds.outWidth, bounds.outHeight);
        while (edge / sample > 4096) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), opts);
        if (bitmap == null) {
            Logx.i("HEIC decode failed, keeping original");
            return null;
        }
        File jpeg = new File(dir, "IMG_" + System.currentTimeMillis() + "_" + index + ".jpg");
        try (FileOutputStream out = new FileOutputStream(jpeg)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                return null;
            }
        } catch (Throwable t) {
            Logx.i("HEIC transcode failed: " + t);
            return null;
        } finally {
            bitmap.recycle();
        }
        Logx.i("transcoded HEIC to " + jpeg.getName());
        return jpeg;
    }

    private static String digest(InputStream in) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            digest.update(buf, 0, n);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
