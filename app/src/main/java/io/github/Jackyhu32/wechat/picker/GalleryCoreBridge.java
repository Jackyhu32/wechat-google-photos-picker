package io.github.Jackyhu32.wechat.picker;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Reflection bridge to WeChat's {@code GalleryCore} singleton (identified via DexKit using
 * {@code "MicroMsg.GalleryCore"} and {@code "stopServices: finish."}).
 *
 * <p>{@code ImagePreviewUI} looks up each preview path via {@code GalleryCore.e(path)}
 * (backed by the static {@code ArrayList} set by {@code void <set>(ArrayList)}) and
 * {@code GalleryCore.g(path)} (backed by the static {@code LinkedHashSet} returned by
 * {@code LinkedHashSet <get>()}). Seeding both with genuine {@code GalleryItem$MediaItem}
 * instances ensures videos are recognized as {@code VideoMediaItem} ({@code type == 2}).
 */
public final class GalleryCoreBridge {
    private static volatile String coreClassName;

    private GalleryCoreBridge() {
    }

    public static void setClassName(String className) {
        if (className != null && !className.isEmpty()) {
            coreClassName = className;
            Logx.i("dexkit gallery core class " + className);
        }
    }

    public static String getClassName() {
        return coreClassName;
    }

    public static boolean seed(ClassLoader classLoader, List<?> mediaItems) {
        if (classLoader == null || mediaItems == null || mediaItems.isEmpty()) {
            return false;
        }
        Class<?> coreClass = resolveCoreClass(classLoader);
        if (coreClass == null) {
            Logx.i("GalleryCore class not found yet");
            return false;
        }
        return seedClass(coreClass, mediaItems);
    }

    static boolean seedClass(Class<?> coreClass, List<?> mediaItems) {
        if (coreClass == null || mediaItems == null || mediaItems.isEmpty()) {
            return false;
        }
        boolean seededList = false;
        boolean seededSet = false;
        for (Method method : coreClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (!seededList
                    && params.length == 1
                    && params[0] == ArrayList.class
                    && method.getReturnType() == void.class) {
                try {
                    method.setAccessible(true);
                    method.invoke(null, new ArrayList<>(mediaItems));
                    seededList = true;
                    Logx.i("seeded GalleryCore preview list via " + coreClass.getName() + "." + method.getName()
                            + " count=" + mediaItems.size());
                } catch (Throwable t) {
                    Logx.i("GalleryCore setPreviewList failed: " + t);
                }
            } else if (!seededSet
                    && params.length == 0
                    && LinkedHashSet.class.isAssignableFrom(method.getReturnType())) {
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(null);
                    if (result instanceof Collection) {
                        @SuppressWarnings("unchecked")
                        Collection<Object> set = (Collection<Object>) result;
                        set.addAll(mediaItems);
                        seededSet = true;
                        Logx.i("seeded GalleryCore selected set via " + coreClass.getName() + "." + method.getName()
                                + " size=" + set.size());
                    }
                } catch (Throwable t) {
                    Logx.i("GalleryCore addSelectedSet failed: " + t);
                }
            }
        }
        return seededList || seededSet;
    }

    private static Class<?> resolveCoreClass(ClassLoader classLoader) {
        String name = coreClassName;
        if (name != null && !name.isEmpty()) {
            try {
                return Class.forName(name, false, classLoader);
            } catch (Throwable ignored) {
            }
        }
        // Fallback for WeChat 8.0.80 if DexKit scan has not finished yet.
        try {
            return Class.forName("sa3.p0", false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
