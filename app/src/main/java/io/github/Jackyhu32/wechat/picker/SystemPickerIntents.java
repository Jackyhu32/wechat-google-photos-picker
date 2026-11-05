package io.github.Jackyhu32.wechat.picker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.MediaStore;

/**
 * Opens Google Photos' own picker. CN HyperOS rewrites
 * {@code device_config mediaprovider/allowed_cloud_providers} to {@code com.miui.gallery}
 * (the rewrite comes from {@code com.miui.securitycenter}), so Google Photos never shows up
 * as a cloud provider inside {@code HyperMainActivity}. This module does not try to undo
 * that whitelist and does not switch the user to an international gallery build.
 *
 * <p>Primary target, checked on device with {@code adb am start}:
 * {@code com.google.android.apps.photos/.picker.external.ExternalPickerActivity}
 * with {@link Intent#ACTION_GET_CONTENT}. {@link MediaStore#ACTION_PICK_IMAGES} (HyperOS)
 * is only used when that component cannot be started.
 */
public final class SystemPickerIntents {
    static final String PRIMARY_ACTION = "android.intent.action.GET_CONTENT";
    static final String PHOTOS_PACKAGE = "com.google.android.apps.photos";
    static final String PHOTOS_ACTIVITY =
            "com.google.android.apps.photos.picker.external.ExternalPickerActivity";
    static final String HYPER_ACTION = "android.provider.action.PICK_IMAGES";
    static final String HYPER_PICKER_PACKAGE = "com.android.photopicker";

    private static final String EXTRA_PICK_IMAGES_MAX = "android.provider.extra.PICK_IMAGES_MAX";
    private static final String EXTRA_PICK_IMAGES_IN_ORDER = "android.provider.extra.PICK_IMAGES_IN_ORDER";
    private static final String[] FALLBACK_PICKER_PACKAGES = {
            "com.google.android.providers.media.module",
            "com.android.providers.media.module"
    };

    private SystemPickerIntents() {
    }

    /** Google Photos external picker. Callers fall back to {@link #buildHyperFallback} on failure. */
    public static Intent build(Context context, LaunchOptions options) {
        Intent intent = new Intent(PRIMARY_ACTION);
        intent.setComponent(new ComponentName(PHOTOS_PACKAGE, PHOTOS_ACTIVITY));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (allowMultiple(options)) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        applyMime(intent, mediaType(options));
        return intent;
    }

    /**
     * Previous system-picker path. Kept so a phone without the Photos component can still
     * open {@code HyperMainActivity}. Not the path WeChat should hit when Photos is installed.
     */
    public static Intent buildHyperFallback(Context context, LaunchOptions options) {
        int max = options == null ? 9 : options.maxCount;
        int media = mediaType(options);
        Intent picker = new Intent(HYPER_ACTION);
        applyMime(picker, media);
        if (max > 1) {
            int limit = max;
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    int systemMax = MediaStore.getPickImagesMaxLimit();
                    if (systemMax > 1) {
                        limit = Math.min(limit, systemMax);
                    }
                } catch (Throwable ignored) {
                    limit = Math.min(limit, 100);
                }
            }
            if (limit > 1) {
                picker.putExtra(EXTRA_PICK_IMAGES_MAX, limit);
                picker.putExtra(EXTRA_PICK_IMAGES_IN_ORDER, true);
            }
        }
        preferCloudCapablePackage(context, picker);
        if (canResolve(context, picker)) {
            return picker;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            Logx.i("ACTION_PICK_IMAGES did not resolve; launching it anyway");
            return picker;
        }
        Intent documents = new Intent(Intent.ACTION_GET_CONTENT);
        documents.addCategory(Intent.CATEGORY_OPENABLE);
        if (max > 1) {
            documents.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        applyMime(documents, media);
        if (canResolve(context, documents)) {
            Logx.i("photo picker missing, falling back to generic ACTION_GET_CONTENT");
            return documents;
        }
        Logx.i("no system picker available");
        return null;
    }

    static int mediaType(LaunchOptions options) {
        return options == null ? LaunchOptions.BOTH : options.mediaType;
    }

    static boolean allowMultiple(LaunchOptions options) {
        int max = options == null ? 9 : options.maxCount;
        return max > 1;
    }

    /** image/*, video/*, or both as a wildcard type plus image and video MIME extras. */
    static String mimeType(int media) {
        if (media == LaunchOptions.VIDEO) {
            return "video/*";
        }
        if (media == LaunchOptions.IMAGE) {
            return "image/*";
        }
        return "*/*";
    }

    /** Set only for mixed image and video launches, via {@link Intent#EXTRA_MIME_TYPES}. */
    static String[] extraMimeTypes(int media) {
        if (media == LaunchOptions.IMAGE || media == LaunchOptions.VIDEO) {
            return null;
        }
        return new String[]{"image/*", "video/*"};
    }

    private static void applyMime(Intent intent, int media) {
        intent.setType(mimeType(media));
        String[] extra = extraMimeTypes(media);
        if (extra != null) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, extra);
        }
    }

    /**
     * When the Hyper fallback runs, keep HyperOS Photo Picker if it is the default handler.
     */
    static boolean keepResolvedPackage(String packageName) {
        return HYPER_PICKER_PACKAGE.equals(packageName);
    }

    private static void preferCloudCapablePackage(Context context, Intent intent) {
        if (context == null) {
            return;
        }
        PackageManager pm = context.getPackageManager();
        ResolveInfo resolved = null;
        try {
            resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        } catch (Throwable t) {
            Logx.i("resolveActivity failed: " + t);
        }
        if (resolved != null && resolved.activityInfo != null) {
            String pkg = resolved.activityInfo.packageName;
            String cls = resolved.activityInfo.name;
            Logx.i("photo picker resolves to " + pkg + "/" + cls);
            if (keepResolvedPackage(pkg)) {
                intent.setPackage(pkg);
                Logx.i("keeping HyperOS photo picker " + pkg);
                return;
            }
        }
        for (String pkg : FALLBACK_PICKER_PACKAGES) {
            Intent probe = new Intent(intent);
            probe.setPackage(pkg);
            if (canResolve(pm, probe)) {
                intent.setPackage(pkg);
                Logx.i("pinned photo picker to " + pkg);
                return;
            }
        }
    }

    private static boolean canResolve(Context context, Intent intent) {
        if (context == null || intent == null) {
            return false;
        }
        return canResolve(context.getPackageManager(), intent);
    }

    private static boolean canResolve(PackageManager pm, Intent intent) {
        try {
            return intent.resolveActivity(pm) != null;
        } catch (Throwable t) {
            Logx.i("resolveActivity failed: " + t);
            return false;
        }
    }
}
