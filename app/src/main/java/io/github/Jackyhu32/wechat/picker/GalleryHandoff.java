package io.github.Jackyhu32.wechat.picker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Hands off media selected from Google Photos when the user tapped the injected
 * 「Google 相簿」 button inside {@code AlbumPreviewUI}.
 *
 * <ul>
 *   <li>For Scan (掃一掃) / OCR / single-select non-chat callers, returns the selection
 *       directly to the caller via {@code setResult(RESULT_OK, selection) + finish()}
 *       with {@code intent.getData()} populated for {@code AsyncObtainImage}.</li>
 *   <li>For Chat / Moments multi-select, constructs genuine {@code GalleryItem$MediaItem}
 *       ({@code ImageMediaItem} / {@code VideoMediaItem}) instances, seeds WeChat's
 *       {@code GalleryCore} cache and {@code preview_media_item_list} extra, and opens
 *       {@code ImagePreviewUI} so the user can review and tap send.</li>
 * </ul>
 */
public final class GalleryHandoff {
    static final int PREVIEW_REQUEST = 0x5F12;
    static final String EXTRA_PREVIEW_MEDIA_ITEMS = "preview_media_item_list";
    static final String EXTRA_PREVIEW_ALL = "preview_all";

    private static final String[] STALE_RESULT_KEYS = {
            WeChatResultContract.CROP_LIST,
            WeChatResultContract.CROP_SINGLE,
            WeChatResultContract.PREVIEW_LIST,
            WeChatResultContract.VIDEO_LIST,
            WeChatResultContract.MIX_LIST,
            WeChatResultContract.SEG_VIDEO,
            WeChatResultContract.SIGHT_PATH,
            WeChatResultContract.SIGHT_THUMB,
            WeChatResultContract.SIGHT_MD5,
            EXTRA_PREVIEW_MEDIA_ITEMS,
            EXTRA_PREVIEW_ALL,
            "key_select_image_list",
            "isPreviewPhoto",
            ResultIntents.RESULT_MARKER
    };

    private GalleryHandoff() {
    }

    public static String deliver(Activity album, Intent selection) {
        return deliver(album, null, selection);
    }

    public static String deliver(Activity album, LaunchOptions launch, Intent selection) {
        if (album == null || selection == null) {
            return "none";
        }
        selection.removeExtra(ResultIntents.RESULT_MARKER);

        ComponentName calling = album.getCallingActivity();
        String callingClass = calling == null ? null : calling.getClassName();
        Intent albumIntent = album.getIntent();
        int querySourceType = albumIntent == null ? -1 : albumIntent.getIntExtra("query_source_type", -1);
        boolean hasScanExtra = detectScanExtra(albumIntent);
        boolean hasChatTarget = hasChatTarget(albumIntent, selection);
        int maxCount = launch != null
                ? launch.maxCount
                : (albumIntent == null ? 9 : albumIntent.getIntExtra(WeChatResultContract.MAX_SELECT, 9));
        List<String> videos = selection.getStringArrayListExtra(WeChatResultContract.VIDEO_LIST);
        boolean hasVideos = videos != null && !videos.isEmpty();

        DeliveryPolicy.Mode mode = DeliveryPolicy.decide(
                calling != null,
                callingClass,
                querySourceType,
                hasScanExtra,
                hasChatTarget,
                maxCount,
                hasVideos
        );
        Logx.i("delivery policy=" + mode
                + " calling=" + callingClass
                + " source=" + querySourceType
                + " scanExtra=" + hasScanExtra
                + " chat=" + hasChatTarget
                + " max=" + maxCount
                + " videos=" + (videos == null ? 0 : videos.size())
                + " dataUri=" + selection.getData());

        if (mode == DeliveryPolicy.Mode.DIRECT_RESULT) {
            Intent direct = new Intent(selection);
            direct.removeExtra(ResultIntents.RESULT_MARKER);
            album.setResult(Activity.RESULT_OK, direct);
            album.finish();
            return "direct-result";
        }

        if (albumIntent != null) {
            clearStaleResultExtras(albumIntent);
            albumIntent.putExtras(selection);
            Logx.i("album intent keys=" + extraKeys(albumIntent));
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

    private static boolean detectScanExtra(Intent albumIntent) {
        if (albumIntent == null || albumIntent.getExtras() == null) {
            return false;
        }
        Bundle extras = albumIntent.getExtras();
        for (String key : extras.keySet()) {
            if (key == null) {
                continue;
            }
            String lower = key.toLowerCase(Locale.US);
            if (lower.contains("scan") || lower.contains("qbar") || lower.contains("ocr")) {
                Object val = extras.get(key);
                if (Boolean.TRUE.equals(val)) {
                    return true;
                }
                if (val instanceof String && !((String) val).isEmpty()) {
                    return true;
                }
                if (val instanceof Integer && ((Integer) val) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasChatTarget(Intent albumIntent, Intent selection) {
        String[] chatKeys = {"GalleryUI_ToUser", "to_user", "toUser", "Chat_User"};
        for (String key : chatKeys) {
            if (hasNonEmptyString(selection, key) || hasNonEmptyString(albumIntent, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonEmptyString(Intent intent, String key) {
        if (intent == null) {
            return false;
        }
        String val = intent.getStringExtra(key);
        return val != null && !val.isEmpty();
    }

    private static void clearStaleResultExtras(Intent intent) {
        if (intent == null) {
            return;
        }
        for (String key : STALE_RESULT_KEYS) {
            intent.removeExtra(key);
        }
    }

    private static boolean openPreview(Activity album, Intent selection) {
        PreviewPlan plan = PreviewPlan.from(
                selection.getStringArrayListExtra(WeChatResultContract.CROP_LIST),
                selection.getStringArrayListExtra(WeChatResultContract.VIDEO_LIST));
        if (plan.previewPaths.isEmpty()) {
            return false;
        }

        // Build genuine GalleryItem$MediaItem objects and seed WeChat's GalleryCore
        // (p0.p(list) + p0.m()) BEFORE launching ImagePreviewUI, matching AlbumPreviewUI.G8().
        ArrayList<Parcelable> parcelableItems = buildPreviewMediaItems(album.getClassLoader(), plan);
        if (!parcelableItems.isEmpty()) {
            GalleryCoreBridge.seed(album.getClassLoader(), parcelableItems);
        }

        Intent preview = new Intent();
        preview.setClassName("com.tencent.mm", PreviewPlan.CLASS_NAME);
        if (album.getIntent() != null && album.getIntent().getExtras() != null) {
            preview.putExtras(album.getIntent().getExtras());
        }
        clearStaleResultExtras(preview);
        preview.putExtras(selection);
        preview.putStringArrayListExtra(WeChatResultContract.PREVIEW_LIST, new ArrayList<>(plan.previewPaths));
        if (!parcelableItems.isEmpty()) {
            preview.putParcelableArrayListExtra(EXTRA_PREVIEW_MEDIA_ITEMS, parcelableItems);
        }
        preview.putExtra(EXTRA_PREVIEW_ALL, false);
        if (!plan.images.isEmpty()) {
            preview.putStringArrayListExtra("key_select_image_list", new ArrayList<>(plan.images));
        }
        if (plan.hasVideos()) {
            preview.putStringArrayListExtra(WeChatResultContract.VIDEO_LIST, new ArrayList<>(plan.videos));
        }
        preview.putExtra("preview_image_index", plan.index);
        preview.putExtra("preview_position", plan.index);
        preview.putExtra("isPreviewPhoto", plan.isPreviewPhoto);
        preview.removeExtra(ResultIntents.RESULT_MARKER);
        try {
            album.startActivityForResult(preview, PREVIEW_REQUEST);
            Logx.i("delivery opened " + PreviewPlan.CLASS_NAME
                    + " images=" + plan.images.size()
                    + " videos=" + plan.videos.size()
                    + " mediaItems=" + parcelableItems.size()
                    + " isPreviewPhoto=" + plan.isPreviewPhoto
                    + " waiting for user send");
            return true;
        } catch (Throwable t) {
            Logx.i("delivery image-preview failed: " + t);
            return false;
        }
    }

    private static ArrayList<Parcelable> buildPreviewMediaItems(ClassLoader loader, PreviewPlan plan) {
        ArrayList<Parcelable> items = new ArrayList<>();
        if (loader == null || plan == null) {
            return items;
        }
        Class<?> itemClass = null;
        try {
            itemClass = Class.forName(MediaItemHook.MEDIA_ITEM_CLASS, false, loader);
        } catch (Throwable t) {
            Logx.i("could not load GalleryItem$MediaItem: " + t);
            return items;
        }
        MediaItemHook.ensureHooked(itemClass);
        for (String path : plan.images) {
            Object item = newMediaItem(itemClass, path, false);
            if (item instanceof Parcelable) {
                items.add((Parcelable) item);
            }
        }
        for (String path : plan.videos) {
            Object item = newMediaItem(itemClass, path, true);
            if (item instanceof Parcelable) {
                items.add((Parcelable) item);
            }
        }
        return items;
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
                MediaItemHook.ensureHooked(itemClass);
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
        VideoRegistry.VideoMeta meta = video ? VideoRegistry.get(path) : null;
        long id = meta != null ? meta.syntheticId : 0L;
        String thumb = (meta != null && meta.thumbPath != null && !meta.thumbPath.isEmpty())
                ? meta.thumbPath : path;
        String mime = video ? (meta != null ? meta.mimeType : "video/mp4") : "";
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
                    Object item = method.invoke(null, kind, id, path, thumb, mime);
                    if (video && meta != null && item != null) {
                        VideoMediaItemPatch.populateVideoItemFields(item, meta);
                    }
                    return item;
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
                    Object item = constructor.newInstance(kind, id, path, thumb, mime);
                    if (video && meta != null) {
                        VideoMediaItemPatch.populateVideoItemFields(item, meta);
                    }
                    return item;
                }
                if (params.length == 0) {
                    Object item = constructor.newInstance();
                    fillPathFields(item, path);
                    if (video && meta != null) {
                        VideoMediaItemPatch.populateVideoItemFields(item, meta);
                    }
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
