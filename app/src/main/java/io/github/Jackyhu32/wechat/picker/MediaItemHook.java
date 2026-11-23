package io.github.Jackyhu32.wechat.picker;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks WeChat's {@code GalleryItem$MediaItem} static factory so that when
 * {@code ImagePreviewUI} misses the MediaStore cache for a video copied into
 * {@code syspicker/} and falls back to {@code MediaItem.a(1, 0, path, path, "")},
 * it receives a genuine {@code VideoMediaItem} ({@code type = 2}) with the extracted
 * JPEG thumbnail and video MIME type instead of a black-screen {@code ImageMediaItem}.
 */
public final class MediaItemHook {
    static final String MEDIA_ITEM_CLASS = "com.tencent.mm.plugin.gallery.model.GalleryItem$MediaItem";
    private static final Set<String> HOOKED_CLASSES = ConcurrentHashMap.newKeySet();

    private MediaItemHook() {
    }

    public static void install(ClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        try {
            Class<?> itemClass = XposedHelpers.findClassIfExists(MEDIA_ITEM_CLASS, classLoader);
            if (itemClass != null) {
                ensureHooked(itemClass);
            }
        } catch (Throwable t) {
            Logx.i("MediaItemHook install failed: " + t);
        }
    }

    public static void ensureHooked(Class<?> itemClass) {
        if (itemClass == null) {
            return;
        }
        String className = itemClass.getName();
        if (!HOOKED_CLASSES.add(className)) {
            return;
        }
        int hooked = 0;
        try {
            for (Method method : itemClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (!itemClass.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                if (!VideoMediaItemPatch.isFactorySignature(method.getParameterTypes())) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        VideoMediaItemPatch.rewriteFactoryArgs(param.args);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        VideoRegistry.VideoMeta meta = VideoMediaItemPatch.findRegisteredVideo(param.args);
                        if (meta != null && param.getResult() != null) {
                            VideoMediaItemPatch.populateVideoItemFields(param.getResult(), meta);
                        }
                    }
                });
                hooked++;
            }
            if (hooked > 0) {
                Logx.i("hooked " + hooked + " MediaItem factory method(s) on " + className);
            }
        } catch (Throwable t) {
            Logx.i("MediaItemHook failed on " + className + ": " + t);
        }
    }
}
