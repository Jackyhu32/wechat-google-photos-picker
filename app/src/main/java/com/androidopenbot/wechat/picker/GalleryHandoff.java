package com.androidopenbot.wechat.picker;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * WeChat 8.0.79 opens AlbumPreviewUI with request code -1, so {@code setResult}
 * never reaches the chat. After Google Photos import, walk the same path the
 * stock album uses: seed its selection, call its confirm method, click 发送,
 * and if that does not finish the screen, open ImagePreviewUI with the paths.
 */
public final class GalleryHandoff {
    /** Written onto the album Intent when the start was not for-result. Survives the tools process. */
    public static final String EXTRA_AUTO_PHOTOS = "wechat_sys_picker_auto_photos";
    static final int PREVIEW_REQUEST = 0x5F12;

    private static volatile List<Method> cachedConfirms;
    private static volatile boolean previewSendArmed;

    private GalleryHandoff() {
    }

    public static List<Method> findConfirmMethods(Activity activity) {
        List<Method> cached = cachedConfirms;
        if (cached != null) {
            return cached;
        }
        synchronized (GalleryHandoff.class) {
            if (cachedConfirms != null) {
                return cachedConfirms;
            }
            List<Method> found = new ArrayList<>(DexKitGalleryFinder.findMethodsUsing(
                    activity, WeChatResultContract.CROP_LIST));
            found.sort(Comparator.comparingInt((Method method) -> score(method)).reversed());
            cachedConfirms = Collections.unmodifiableList(found);
            Logx.i("delivery confirm methods=" + cachedConfirms.size());
            return cachedConfirms;
        }
    }

    public static String deliver(Activity album, Intent selection, List<Method> confirms) {
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
        try {
            album.setResult(Activity.RESULT_OK, selection);
        } catch (Throwable t) {
            Logx.i("delivery stage setResult failed: " + t);
        }
        StringBuilder how = new StringBuilder();
        if (clickSend(album)) {
            how.append("send-button");
            if (album.isFinishing()) {
                return how.toString();
            }
        }
        String confirm = invokeMatching(album, confirms, "AlbumPreviewUI");
        if (confirm != null) {
            if (how.length() > 0) {
                how.append('+');
            }
            how.append(confirm);
            if (album.isFinishing()) {
                return how.toString();
            }
        }
        if (openPreview(album, selection)) {
            if (how.length() > 0) {
                how.append('+');
            }
            how.append("image-preview");
            return how.toString();
        }
        if (!album.isFinishing()) {
            album.finish();
            if (how.length() > 0) {
                how.append('+');
            }
            how.append("set-result");
        }
        return how.length() == 0 ? "set-result" : how.toString();
    }

    /** ImagePreviewUI was opened by us; run its own send once the view exists. */
    public static String finishPreview(Activity preview, List<Method> confirms) {
        if (preview == null) {
            return "image-preview-send";
        }
        String confirm = invokeMatching(preview, confirms, "ImagePreviewUI");
        boolean clicked = clickSend(preview);
        if (confirm != null && clicked) {
            return "image-preview-confirm+send";
        }
        if (confirm != null) {
            return "image-preview-confirm";
        }
        if (clicked) {
            return "image-preview-send";
        }
        return "image-preview-send-missed";
    }

    public static void armPreviewSend() {
        previewSendArmed = true;
    }

    public static boolean consumePreviewSend(Activity activity) {
        if (!previewSendArmed || activity == null) {
            return false;
        }
        String name = activity.getClass().getName();
        if (name == null || !name.contains("ImagePreviewUI")) {
            return false;
        }
        previewSendArmed = false;
        return true;
    }

    public static boolean clickSend(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return false;
        }
        View match = findSend(activity.getWindow().getDecorView());
        if (match == null) {
            Logx.i("delivery send button not found");
            return false;
        }
        View target = match;
        while (target != null && !target.hasOnClickListeners() && target.getParent() instanceof View) {
            target = (View) target.getParent();
        }
        if (target == null) {
            target = match;
        }
        CharSequence label = match instanceof TextView ? ((TextView) match).getText() : match.getContentDescription();
        Logx.i("delivery clicking label=" + label);
        target.setEnabled(true);
        target.performClick();
        return true;
    }

    public static boolean looksLikeSendLabel(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return trimmed.startsWith("发送")
                || trimmed.startsWith("傳送")
                || trimmed.startsWith("完成")
                || trimmed.startsWith("Send")
                || trimmed.startsWith("Done");
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
        preview.removeExtra(EXTRA_AUTO_PHOTOS);
        try {
            armPreviewSend();
            album.startActivityForResult(preview, PREVIEW_REQUEST);
            Logx.i("delivery opened " + PreviewPlan.CLASS_NAME
                    + " images=" + plan.images.size()
                    + " videos=" + plan.videos.size());
            return true;
        } catch (Throwable t) {
            previewSendArmed = false;
            Logx.i("delivery image-preview failed: " + t);
            return false;
        }
    }

    private static String invokeMatching(Activity activity, List<Method> confirms, String classPart) {
        if (confirms == null || classPart == null) {
            return null;
        }
        for (Method method : confirms) {
            if (method == null || method.getDeclaringClass() == null) {
                continue;
            }
            if (!method.getDeclaringClass().getName().contains(classPart)) {
                continue;
            }
            if (ConfirmRanking.isLifecycle(method.getName())) {
                continue;
            }
            Object receiver = receiverFor(activity, method);
            if (receiver == null) {
                Logx.i("delivery skip " + method.getDeclaringClass().getName() + "#" + method.getName() + " no receiver");
                continue;
            }
            Object[] args = argsFor(activity, method);
            if (args == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(receiver, args);
                String invoked = "confirm " + method.getDeclaringClass().getSimpleName() + "#" + method.getName();
                Logx.i("delivery invoked " + invoked + " finishing=" + activity.isFinishing());
                return invoked;
            } catch (Throwable t) {
                Logx.i("delivery invoke failed " + method.getName() + ": " + t);
            }
        }
        return null;
    }

    private static Object receiverFor(Activity activity, Method method) {
        Class<?> owner = method.getDeclaringClass();
        if (Modifier.isStatic(method.getModifiers())) {
            return null;
        }
        if (owner.isInstance(activity)) {
            return activity;
        }
        Class<?> type = activity.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !owner.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(activity);
                    if (value != null) {
                        return value;
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object[] argsFor(Activity activity, Method method) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length == 0) {
            return new Object[0];
        }
        if (params.length == 1 && View.class.isAssignableFrom(params[0])) {
            View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            return new Object[]{decor};
        }
        return null;
    }

    private static int score(Method method) {
        String ret = method.getReturnType() == void.class ? "void" : method.getReturnType().getName();
        return ConfirmRanking.score(new ConfirmRanking.Target(
                method.getDeclaringClass().getName(),
                method.getName(),
                method.getParameterCount(),
                ret,
                true));
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

    private static View findSend(View root) {
        View found = null;
        if (root instanceof TextView) {
            TextView text = (TextView) root;
            CharSequence label = text.getText();
            CharSequence description = text.getContentDescription();
            if (looksLikeSendLabel(label == null ? null : label.toString())
                    || looksLikeSendLabel(description == null ? null : description.toString())) {
                found = text;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = findSend(group.getChildAt(i));
                if (child != null) {
                    found = child;
                }
            }
        }
        return found;
    }
}
