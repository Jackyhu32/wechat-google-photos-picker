package com.androidopenbot.wechat.picker;

import android.content.Intent;

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
        for (Map.Entry<String, Object> entry : extras.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof ArrayList) {
                @SuppressWarnings("unchecked")
                ArrayList<String> list = (ArrayList<String>) value;
                intent.putStringArrayListExtra(entry.getKey(), list);
            } else if (value instanceof String) {
                intent.putExtra(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                intent.putExtra(entry.getKey(), (Boolean) value);
            }
        }
        return intent;
    }
}
