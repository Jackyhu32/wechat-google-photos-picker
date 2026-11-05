package com.androidopenbot.wechat.picker;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Blocks the chat UI while a cloud download is copied into WeChat's files. */
public final class ImportOverlay {
    public static final int ID = 0x57435043;

    private ImportOverlay() {
    }

    public static void show(Activity activity) {
        ViewGroup parent = parent(activity);
        if (parent == null || parent.findViewById(ID) != null) {
            return;
        }
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setId(ID);
        overlay.setClickable(true);
        overlay.setBackgroundColor(0xB31C1915);
        TextView label = new TextView(activity);
        label.setText("正在匯入 Google 相簿的檔案…");
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setGravity(Gravity.CENTER);
        int pad = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        label.setPadding(pad, pad, pad, pad);
        overlay.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        parent.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    public static void hide(Activity activity) {
        ViewGroup parent = parent(activity);
        if (parent == null) {
            return;
        }
        View overlay = parent.findViewById(ID);
        if (overlay != null) {
            parent.removeView(overlay);
        }
    }

    private static ViewGroup parent(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return null;
        }
        View decor = activity.getWindow().getDecorView();
        if (decor instanceof FrameLayout) {
            return (FrameLayout) decor;
        }
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            return (ViewGroup) content;
        }
        return null;
    }
}
