package io.github.Jackyhu32.wechat.picker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Intent extras WeChat's album callers have read for years.
 * Keys are stable string literals (they cross plugin boundaries), not obfuscated fields.
 * Duplicate paths are removed because WeChat dedupes and then indexes off the end.
 */
public final class WeChatResultContract {
    public static final String CROP_LIST = "CropImage_OutputPath_List";
    public static final String CROP_SINGLE = "CropImage_OutputPath";
    public static final String PREVIEW_LIST = "preview_image_list";
    public static final String COMPRESS = "CropImage_Compress_Img";
    public static final String SEND_RAW = "send_raw_img";
    public static final String IS_TAKE_PHOTO = "isTakePhoto";
    public static final String VIDEO_LIST = "key_select_video_list";
    public static final String MIX_LIST = "key_select_mix_media_list";
    public static final String SEG_VIDEO = "K_SEGMENTVIDEOPATH";
    public static final String SIGHT_PATH = "KSightPath";
    public static final String SIGHT_THUMB = "KSightThumbPath";
    public static final String SIGHT_MD5 = "sight_md5";
    public static final String MAX_SELECT = "max_select_count";
    public static final String QUERY_MEDIA = "query_media_type";

    public static final String[] PASSTHROUGH = {
            "GalleryUI_ToUser",
            "GalleryUI_FromUser",
            "to_user",
            "toUser",
            "from_user",
            "Chat_User"
    };

    private WeChatResultContract() {
    }

    public static final class Item {
        public final boolean video;
        public final String path;
        public final String thumbPath;
        public final String md5;

        public Item(boolean video, String path, String thumbPath, String md5) {
            this.video = video;
            this.path = path;
            this.thumbPath = thumbPath;
            this.md5 = md5;
        }
    }

    /** @return extras to put on the result Intent, or null when nothing usable was selected */
    public static Map<String, Object> build(List<Item> items, boolean sendRaw, Map<String, String> passthrough) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> images = new LinkedHashSet<>();
        LinkedHashSet<String> videos = new LinkedHashSet<>();
        ArrayList<String> ordered = new ArrayList<>();
        Item onlyVideo = null;
        for (Item item : items) {
            if (item == null || item.path == null || item.path.isEmpty()) {
                continue;
            }
            if (item.video) {
                if (videos.add(item.path)) {
                    ordered.add(item.path);
                    onlyVideo = item;
                }
            } else if (images.add(item.path)) {
                ordered.add(item.path);
            }
        }
        if (images.isEmpty() && videos.isEmpty()) {
            return null;
        }
        if (videos.size() != 1 || !images.isEmpty()) {
            onlyVideo = null;
        }

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put(IS_TAKE_PHOTO, Boolean.FALSE);
        extras.put(COMPRESS, Boolean.valueOf(!sendRaw));
        extras.put(SEND_RAW, Boolean.valueOf(sendRaw));
        if (passthrough != null) {
            for (Map.Entry<String, String> entry : passthrough.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    extras.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (!images.isEmpty()) {
            ArrayList<String> imageList = new ArrayList<>(images);
            extras.put(CROP_LIST, imageList);
            extras.put(PREVIEW_LIST, new ArrayList<>(imageList));
            extras.put(CROP_SINGLE, imageList.get(0));
        }
        if (!videos.isEmpty()) {
            extras.put(VIDEO_LIST, new ArrayList<>(videos));
        }
        if (onlyVideo != null) {
            extras.put(SEG_VIDEO, onlyVideo.path);
            extras.put(SIGHT_PATH, onlyVideo.path);
            if (onlyVideo.thumbPath != null && !onlyVideo.thumbPath.isEmpty()) {
                extras.put(SIGHT_THUMB, onlyVideo.thumbPath);
            }
            if (onlyVideo.md5 != null && !onlyVideo.md5.isEmpty()) {
                extras.put(SIGHT_MD5, onlyVideo.md5);
            }
        } else if (!images.isEmpty() && !videos.isEmpty()) {
            // Only the mixed case. Putting images here as well makes some builds send twice.
            extras.put(MIX_LIST, ordered);
        }
        return extras;
    }
}
