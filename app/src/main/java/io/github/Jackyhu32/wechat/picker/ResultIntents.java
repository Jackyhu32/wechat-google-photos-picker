package io.github.Jackyhu32.wechat.picker;

import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Map;

public final class ResultIntents {
    public static final String RESULT_MARKER = "wechat_sys_picker_result";

    private ResultIntents() {
    }

    public static Intent toIntent(Map<String, Object> extras) {
        Intent intent = new Intent();
        intent.putExtra(RESULT_MARKER, true);
        if (extras == null) {
            return intent;
        }
        String primaryPath = null;
        for (Map.Entry<String, Object> entry : extras.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof ArrayList) {
                @SuppressWarnings("unchecked")
                ArrayList<String> list = (ArrayList<String>) value;
                intent.putStringArrayListExtra(entry.getKey(), list);
                if (primaryPath == null && !list.isEmpty()
                        && (WeChatResultContract.CROP_LIST.equals(entry.getKey())
                        || WeChatResultContract.VIDEO_LIST.equals(entry.getKey()))) {
                    primaryPath = list.get(0);
                }
            } else if (value instanceof String) {
                intent.putExtra(entry.getKey(), (String) value);
                if (WeChatResultContract.CROP_SINGLE.equals(entry.getKey())) {
                    primaryPath = (String) value;
                }
            } else if (value instanceof Boolean) {
                intent.putExtra(entry.getKey(), (Boolean) value);
            }
        }
        // Matches WeChat AlbumPreviewUI: BaseScanUI.onActivityResult(4660/561) resolves the
        // image path via AsyncObtainImage (k1.b) from intent.getData().getPath(), not from
        // CropImage_OutputPath_List. Using Uri.parse(Uri.encode(path)) avoids FileUriExposedException.
        if (primaryPath != null && !primaryPath.isEmpty()) {
            intent.setData(Uri.parse(Uri.encode(primaryPath)));
        }
        return intent;
    }
}
