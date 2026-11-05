package io.github.Jackyhu32.wechat.picker;

import java.util.List;

/**
 * Picks the gallery method that stock "send" uses.
 * Lifecycle methods that merely mention the extra are not confirm handlers.
 */
public final class ConfirmRanking {
    public static final class Target {
        public final String className;
        public final String methodName;
        public final int paramCount;
        public final String returnType;
        public final boolean usesCropList;

        public Target(String className, String methodName, int paramCount, String returnType, boolean usesCropList) {
            this.className = className;
            this.methodName = methodName;
            this.paramCount = paramCount;
            this.returnType = returnType;
            this.usesCropList = usesCropList;
        }
    }

    private ConfirmRanking() {
    }

    public static int score(Target target) {
        if (target == null || !target.usesCropList || target.className == null || target.methodName == null) {
            return -1;
        }
        boolean album = target.className.contains("AlbumPreviewUI");
        boolean preview = target.className.contains("ImagePreviewUI");
        if (!album && !preview) {
            return -1;
        }
        if (isLifecycle(target.methodName)) {
            return -1;
        }
        int score = album ? 40 : 15;
        if ("void".equals(target.returnType) || "V".equals(target.returnType)) {
            score += 20;
        }
        if (target.paramCount == 0) {
            score += 50;
        } else if (target.paramCount == 1) {
            score += 30;
        } else if (target.paramCount > 2) {
            score -= 10;
        }
        if ("onClick".equals(target.methodName)) {
            score += 10;
        }
        if (target.className.contains("$")) {
            score -= 5;
        }
        return score;
    }

    public static Target best(List<Target> targets) {
        Target best = null;
        int bestScore = 0;
        if (targets == null) {
            return null;
        }
        for (Target target : targets) {
            int value = score(target);
            if (value > bestScore) {
                bestScore = value;
                best = target;
            }
        }
        return best;
    }

    public static boolean isLifecycle(String name) {
        return "onCreate".equals(name)
                || "onActivityResult".equals(name)
                || "onResume".equals(name)
                || "onStart".equals(name)
                || "onPause".equals(name)
                || "onStop".equals(name)
                || "onDestroy".equals(name)
                || "onNewIntent".equals(name)
                || "onSaveInstanceState".equals(name)
                || "onRestoreInstanceState".equals(name)
                || "onActivityCreated".equals(name);
    }
}
