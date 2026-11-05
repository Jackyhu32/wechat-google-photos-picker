package com.androidopenbot.wechat.picker;

import android.app.Activity;
import android.content.Intent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WeChat 8.0.79 opens AlbumPreviewUI with request code -1, so {@code setResult}
 * never reaches the chat. After the user picks in Google Photos, seed the album
 * selection and open ImagePreviewUI. Do not tap 发送 and do not call the confirm
 * method that sends the message; the user edits on that screen and taps send.
 */
public final class GalleryHandoff {
    static final int PREVIEW_REQUEST = 0x5F12;

    private GalleryHandoff() {
    }

    public static String deliver(Activity album, Intent selection) {
        if (album == null || selection == null) {
            return "none";
        }
        selection.removeExtra(ResultIntents.RESULT_MARKER);
        if (album.getIntent() != null) {
            album.getIntent().putExtras(selection);
            Logx.i("album intent keys=" + extraKeys(album.getIntent()));
        }
        int seeded = seedSelection(album, selection);
        Logx.i("delivery seededLists=" + seeded);
        if (openPreview(album, selection)) {
            return "image-preview";
        }
        Logx.i("delivery preview unavailable; album left open with seeded paths");
        return "seeded-album";
    }

    public static String extraKeys(Intent intent) {
        if (intent == null || intent.getExtras() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String key : intent.getExtras().keySet()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(key);
        }
        return builder.toString();
    }

    private static boolean openPreview(Activity album, Intent selection) {
        PreviewPlan plan = PreviewPlan.from(
                selection.getStringArrayListExtra(WeChatResultContract.CROP_LIST),
                selection.getStringArrayListExtra(WeChatResultContract.VIDEO_LIST));
        if (plan.previewPaths.isEmpty()) {
            return false;
        }
        Intent preview = new Intent();
        preview.setClassName("com.tencent.mm", PreviewPlan.CLASS_NAME);
        if (album.getIntent() != null && album.getIntent().getExtras() != null) {
            preview.putExtras(album.getIntent().getExtras());
        }
        preview.putExtras(selection);
        preview.putStringArrayListExtra(WeChatResultContract.PREVIEW_LIST, new ArrayList<>(plan.previewPaths));
        preview.putStringArrayListExtra("key_select_image_list", new ArrayList<>(plan.images));
        preview.putExtra("preview_image_index", plan.index);
        preview.putExtra("preview_position", plan.index);
        preview.putExtra("isPreviewPhoto", true);
        preview.removeExtra(ResultIntents.RESULT_MARKER);
        try {
            album.startActivityForResult(preview, PREVIEW_REQUEST);
            Logx.i("delivery opened " + PreviewPlan.CLASS_NAME
                    + " images=" + plan.images.size()
                    + " videos=" + plan.videos.size()
                    + " waiting for user send");
            return true;
        } catch (Throwable t) {
            Logx.i("delivery image-preview failed: " + t);
            return false;
        }
    }

    static int seedSelection(Activity activity, Intent selection) {
        List<String> images = selection.getStringArrayListExtra(WeChatResultContract.CROP_LIST);
        List<String> videos = selection.getStringArrayListExtra(WeChatResultContract.VIDEO_LIST);
        if (images == null) {
            images = Collections.emptyList();
        }
        if (videos == null) {
            videos = Collections.emptyList();
        }
        int seeded = 0;
        Class<?> type = activity.getClass();
        while (type != null && type != Activity.class && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                seeded += seedField(activity, field, images, videos);
            }
            type = type.getSuperclass();
        }
        return seeded;
    }

    @SuppressWarnings("unchecked")
    private static int seedField(Activity activity, Field field, List<String> images, List<String> videos) {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType)) {
            return 0;
        }
        ParameterizedType parameterized = (ParameterizedType) generic;
        Type raw = parameterized.getRawType();
        if (!(raw instanceof Class) || !List.class.isAssignableFrom((Class<?>) raw)) {
            return 0;
        }
        Type[] args = parameterized.getActualTypeArguments();
        if (args.length != 1) {
            return 0;
        }
        try {
            field.setAccessible(true);
            Object current = field.get(activity);
            List<Object> list;
            if (current instanceof List) {
                list = (List<Object>) current;
            } else if (current == null && !Modifier.isFinal(field.getModifiers())) {
                list = new ArrayList<>();
                field.set(activity, list);
            } else {
                return 0;
            }
            if (args[0] == String.class) {
                addStrings(list, images);
                addStrings(list, videos);
                Logx.i("delivery seeded " + field.getName() + " strings=" + list.size());
                return 1;
            }
            if (args[0] instanceof Class && isMediaItem((Class<?>) args[0])) {
                Class<?> itemClass = (Class<?>) args[0];
                for (String path : images) {
                    addItem(list, itemClass, path, false);
                }
                for (String path : videos) {
                    addItem(list, itemClass, path, true);
                }
                Logx.i("delivery seeded " + field.getName() + " media=" + list.size());
                return 1;
            }
        } catch (Throwable t) {
            Logx.i("delivery seed failed " + field.getName() + ": " + t);
        }
        return 0;
    }

    private static boolean isMediaItem(Class<?> type) {
        String name = type.getName();
        return name.contains("MediaItem") || name.contains("GalleryItem");
    }

    @SuppressWarnings("unchecked")
    private static void addStrings(List<Object> list, List<String> paths) {
        for (String path : paths) {
            if (path != null && !path.isEmpty() && !list.contains(path)) {
                list.add(path);
            }
        }
    }

    private static void addItem(List<Object> list, Class<?> itemClass, String path, boolean video) {
        if (path == null || path.isEmpty()) {
            return;
        }
        Object item = newMediaItem(itemClass, path, video);
        if (item != null) {
            list.add(item);
        }
    }

    static Object newMediaItem(Class<?> itemClass, String path, boolean video) {
        int kind = video ? 2 : 1;
        try {
            for (Method method : itemClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 5
                        && params[0] == int.class
                        && params[1] == long.class
                        && params[2] == String.class
                        && params[3] == String.class
                        && params[4] == String.class
                        && itemClass.isAssignableFrom(method.getReturnType())) {
                    method.setAccessible(true);
                    return method.invoke(null, kind, 0L, path, path, "");
                }
            }
            for (Constructor<?> constructor : itemClass.getDeclaredConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                constructor.setAccessible(true);
                if (params.length == 5
                        && params[0] == int.class
                        && params[1] == long.class
                        && params[2] == String.class
                        && params[3] == String.class
                        && params[4] == String.class) {
                    return constructor.newInstance(kind, 0L, path, path, path);
                }
                if (params.length == 0) {
                    Object item = constructor.newInstance();
                    fillPathFields(item, path);
                    return item;
                }
            }
        } catch (Throwable t) {
            Logx.i("delivery media item failed: " + t);
        }
        return null;
    }

    private static void fillPathFields(Object item, String path) throws IllegalAccessException {
        for (Field field : item.getClass().getDeclaredFields()) {
            if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(item);
            if (value == null || String.valueOf(value).isEmpty()) {
                field.set(item, path);
            }
        }
    }
}
