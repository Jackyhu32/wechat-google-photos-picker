package io.github.Jackyhu32.wechat.picker;

import android.content.Context;
import android.os.Build;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Adds renamed gallery activities to the match set. The literal {@code AlbumPreviewUI}
 * hook still works when this scan fails.
 */
public final class DexKitGalleryFinder {
    private static volatile boolean nativeLoaded;

    private DexKitGalleryFinder() {
    }

    public static void discover(Context context, Set<String> into) {
        if (context == null) {
            return;
        }
        String apk = context.getApplicationInfo().sourceDir;
        try {
            loadNative(context);
        } catch (Throwable t) {
            Logx.i("libdexkit load failed: " + t);
            return;
        }
        try (DexKitBridge bridge = DexKitBridge.create(apk)) {
            int added = 0;
            added += collect(into, bridge.findClass(FindClass.create().matcher(
                    ClassMatcher.create().className("AlbumPreviewUI", StringMatchType.EndsWith, false)
            )));
            added += collect(into, bridge.findClass(FindClass.create()
                    .searchPackages("com.tencent.mm.plugin.gallery")
                    .matcher(ClassMatcher.create().usingEqStrings(Arrays.asList(
                            "CropImage_OutputPath_List",
                            "max_select_count"
                    )))));
            Logx.i("dexkit gallery classes added " + added + ", set size " + into.size());
        } catch (Throwable t) {
            Logx.i("dexkit discover failed: " + t);
        }
    }

    private static int collect(Set<String> into, Iterable<ClassData> classes) {
        int added = 0;
        if (classes == null) {
            return 0;
        }
        for (ClassData data : classes) {
            String name = data.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            boolean album = name.endsWith("AlbumPreviewUI");
            if (!album && !PickerClassMatcher.isSafeDiscovered(name)) {
                Logx.i("dexkit ignored " + name);
                continue;
            }
            if (into.add(name)) {
                added++;
                Logx.i("dexkit gallery class " + name);
            }
        }
        return added;
    }

    private static synchronized void loadNative(Context context) throws Exception {
        if (nativeLoaded) {
            return;
        }
        try {
            System.loadLibrary("dexkit");
            nativeLoaded = true;
            Logx.i("loaded libdexkit with loadLibrary");
            return;
        } catch (Throwable first) {
            Logx.i("loadLibrary dexkit failed, extracting: " + first);
        }
        String moduleApk = moduleApkPath();
        if (moduleApk == null) {
            throw new IllegalStateException("module apk path unknown");
        }
        String abi = abiFolder();
        File out = new File(context.getCodeCacheDir(), "libdexkit.so");
        try (ZipFile zip = new ZipFile(moduleApk)) {
            ZipEntry entry = zip.getEntry("lib/" + abi + "/libdexkit.so");
            if (entry == null) {
                throw new IllegalStateException("libdexkit.so missing for " + abi);
            }
            try (InputStream in = zip.getInputStream(entry);
                 FileOutputStream dest = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    dest.write(buf, 0, n);
                }
            }
        }
        System.load(out.getAbsolutePath());
        nativeLoaded = true;
        Logx.i("loaded libdexkit from " + out.getAbsolutePath());
    }

    private static String moduleApkPath() {
        if (MainHook.modulePath != null) {
            return MainHook.modulePath;
        }
        try {
            String path = DexKitGalleryFinder.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            if (path != null && path.endsWith(".apk")) {
                return path;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String abiFolder() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)
                    || "x86_64".equals(abi) || "x86".equals(abi)) {
                return abi;
            }
        }
        return "arm64-v8a";
    }
}
