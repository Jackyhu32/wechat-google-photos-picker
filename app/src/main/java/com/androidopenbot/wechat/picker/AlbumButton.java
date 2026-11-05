package com.androidopenbot.wechat.picker;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Floating entry shown on WeChat's own album when that screen still opens. */
public final class AlbumButton {
    public static final int ID = 0x57435042;

    private AlbumButton() {
    }

    public static void attach(Activity activity, View.OnClickListener listener) {
        ViewGroup parent = contentParent(activity);
        if (parent == null) {
            return;
        }
        View existing = parent.findViewById(ID);
        if (existing != null) {
            existing.setOnClickListener(listener);
            return;
        }
        TextView button = new TextView(activity);
        button.setId(ID);
        button.setText("Google 相簿");
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        int padH = dp(activity, 18);
        int padV = dp(activity, 10);
        button.setPadding(padH, padV, padH, padV);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF1A73E8);
        background.setCornerRadius(dp(activity, 22));
        button.setBackground(background);
        button.setElevation(dp(activity, 6));
        button.setOnClickListener(listener);
        button.setContentDescription("Google 相簿");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        params.bottomMargin = dp(activity, 96);
        parent.addView(button, params);
    }

    public static void detach(Activity activity) {
        ViewGroup parent = contentParent(activity);
        if (parent == null) {
            return;
        }
        View existing = parent.findViewById(ID);
        if (existing != null) {
            parent.removeView(existing);
        }
    }

    private static ViewGroup contentParent(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof FrameLayout) {
            return (FrameLayout) content;
        }
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor instanceof FrameLayout) {
            return (FrameLayout) decor;
        }
        return null;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
