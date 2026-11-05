package com.androidopenbot.wechat.picker;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.MediaStore;

/**
 * Builds {@link MediaStore#ACTION_PICK_IMAGES} so Google Photos can register as the
 * cloud media provider inside the system picker.
 * On the Redmi K100 Pro Max, that picker is
 * {@code com.android.photopicker/com.android.photopicker.hyper.HyperMainActivity}.
 * Keep that package when it is the default. Pinning the mainline MediaProvider
 * instead would hide HyperMainActivity.
 */
public final class SystemPickerIntents {
    static final String HYPER_PICKER_PACKAGE = "com.android.photopicker";
    private static final String ACTION_PICK_IMAGES = "android.provider.action.PICK_IMAGES";
    private static final String EXTRA_PICK_IMAGES_MAX = "android.provider.extra.PICK_IMAGES_MAX";
    private static final String EXTRA_PICK_IMAGES_IN_ORDER = "android.provider.extra.PICK_IMAGES_IN_ORDER";
    private static final String[] FALLBACK_PICKER_PACKAGES = {
            "com.google.android.providers.media.module",
            "com.android.providers.media.module"
    };

    private SystemPickerIntents() {
    }

    public static Intent build(Context context, LaunchOptions options) {
        int max = options == null ? 9 : options.maxCount;
        int media = options == null ? LaunchOptions.BOTH : options.mediaType;
        Intent picker = new Intent(ACTION_PICK_IMAGES);
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
            // Package visibility can hide the platform picker from resolveActivity
            // even though startActivity still delivers ACTION_PICK_IMAGES.
            Logx.i("ACTION_PICK_IMAGES did not resolve; launching it anyway");
            return picker;
        }
        Intent documents = buildGetContent(max, media);
        if (canResolve(context, documents)) {
            Logx.i("photo picker missing, falling back to ACTION_GET_CONTENT");
            return documents;
        }
        Logx.i("no system picker available");
        return null;
    }

    private static Intent buildGetContent(int max, int media) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (max > 1) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        applyMime(intent, media);
        return intent;
    }

    private static void applyMime(Intent intent, int media) {
        if (media == LaunchOptions.VIDEO) {
            intent.setType("video/*");
        } else if (media == LaunchOptions.IMAGE) {
            intent.setType("image/*");
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        }
    }

    /**
     * HyperOS Photo Picker should stay the handler. Google Photos is selected
     * inside it as the cloud media app, not by replacing the activity.
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
