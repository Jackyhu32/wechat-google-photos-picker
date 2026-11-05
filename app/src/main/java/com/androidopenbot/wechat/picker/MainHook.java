package com.androidopenbot.wechat.picker;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Replaces WeChat's album picker with Google Photos' external picker, then hands back
 * ordinary file paths in the extras WeChat already reads.
 */
public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    static final String TARGET = "com.tencent.mm";
    /** Request code used only by the fallback button inside AlbumPreviewUI. */
    static final int INJECT_RC = 0x5F11;

    static volatile String modulePath;

    private static final Set<String> DISCOVERED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WeChatSysPicker");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object LOCK = new Object();
    private static final Map<Activity, Map<Integer, Pending>> PENDING = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> BYPASS = new ThreadLocal<>();
    private static final AtomicBoolean SCANNED = new AtomicBoolean(false);

    static final class Pending {
        final LaunchOptions launch;
        final boolean injected;

        Pending(LaunchOptions launch, boolean injected) {
            this.launch = launch;
            this.injected = injected;
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET.equals(lpparam.packageName)) {
            return;
        }
        String process = processName();
        Logx.i("loaded in " + process);
        hookExecStart();
        hookDispatchResult();
        hookResume();
        scheduleDiscover(process);
    }

    private void hookExecStart() {
        try {
            XposedBridge.hookAllMethods(Instrumentation.class, "execStartActivity", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        onExecStart(param);
                    } catch (Throwable t) {
                        Logx.i("execStart hook error: " + t);
                    }
                }
            });
            Logx.i("hooked Instrumentation.execStartActivity");
        } catch (Throwable t) {
            Logx.i("execStart hook failed: " + t);
        }
    }

    private void onExecStart(XC_MethodHook.MethodHookParam param) {
        int intentIndex = -1;
        Intent intent = null;
        for (int i = 0; i < param.args.length; i++) {
            if (param.args[i] instanceof Intent) {
                intent = (Intent) param.args[i];
                intentIndex = i;
                break;
            }
        }
        if (intent == null || intent.getComponent() == null) {
            return;
        }
        String className = intent.getComponent().getClassName();
        if (!PickerClassMatcher.isPickerClass(className, DISCOVERED)) {
            return;
        }
        ModulePrefs prefs = ModulePrefs.load();
        if (!prefs.replacePicker) {
            Logx.i("replace off, keep " + className);
            return;
        }
        Activity activity = findActivity(param.args);
        int requestCode = requestCodeAfter(param.args, intentIndex);
        if (requestCode < 0) {
            // 8.0.79 starts AlbumPreviewUI with startActivity (rc=-1). Leave the stock
            // album up. The user taps 「Google 相簿」; replacing this start would drop the result.
            Logx.i("album start is not for-result (" + className + " rc=" + requestCode
                    + "), leaving AlbumPreviewUI up for the Google 相簿 button");
            return;
        }
        if (activity == null) {
            Logx.i("album start has no caller activity (" + className + " rc=" + requestCode + ")");
            return;
        }
        LaunchOptions launch = LaunchOptionsReader.fromIntent(intent, prefs.forceRaw);
        Intent photos = SystemPickerIntents.build(activity, launch);
        if (photos == null) {
            return;
        }
        remember(activity, requestCode, new Pending(launch, false));
        int started = launchPhotos(param, intentIndex, photos);
        if (started == LAUNCH_OK) {
            Logx.i("replaced " + className
                    + " with Google Photos ExternalPicker"
                    + " rc=" + requestCode
                    + " max=" + launch.maxCount
                    + " media=" + launch.mediaType
                    + " raw=" + launch.sendRaw);
            return;
        }
        if (started == LAUNCH_ERROR) {
            take(activity, requestCode);
            return;
        }
        Intent hyper = SystemPickerIntents.buildHyperFallback(activity, launch);
        if (hyper == null) {
            take(activity, requestCode);
            Logx.i("Google Photos ExternalPicker failed and no Hyper fallback is available");
            return;
        }
        param.args[intentIndex] = hyper;
        Logx.i("Google Photos ExternalPicker unavailable, falling back to ACTION_PICK_IMAGES"
                + " rc=" + requestCode);
    }

    private static final int LAUNCH_OK = 0;
    private static final int LAUNCH_FALLBACK = 1;
    private static final int LAUNCH_ERROR = 2;

    /**
     * Starts the Photos intent through the original {@code execStartActivity}.
     * On success the original call is skipped so the album activity is not also opened.
     * Package visibility inside WeChat often hides Google Photos from {@code resolveActivity},
     * so a failed lookup must not be treated as "Photos is missing" — only a real start failure
     * falls through to Hyper.
     */
    private int launchPhotos(XC_MethodHook.MethodHookParam param, int intentIndex, Intent photos) {
        Object[] args = param.args.clone();
        args[intentIndex] = photos;
        try {
            Object result = XposedBridge.invokeOriginalMethod(param.method, param.thisObject, args);
            param.setResult(result);
            return LAUNCH_OK;
        } catch (Throwable t) {
            Throwable missing = activityMissing(t);
            if (missing != null) {
                Logx.i("Google Photos ExternalPicker failed: " + missing);
                return LAUNCH_FALLBACK;
            }
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            Logx.i("Google Photos ExternalPicker error: " + cause);
            param.setThrowable(cause);
            return LAUNCH_ERROR;
        }
    }

    private static Throwable activityMissing(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof ActivityNotFoundException || current instanceof SecurityException) {
                return current;
            }
            Throwable next = current.getCause();
            if (next == null || next == current) {
                return null;
            }
            current = next;
        }
        return null;
    }

    private void hookDispatchResult() {
        try {
            XposedBridge.hookAllMethods(Activity.class, "dispatchActivityResult", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(BYPASS.get())) {
                        return;
                    }
                    try {
                        onDispatch(param);
                    } catch (Throwable t) {
                        Logx.i("dispatch hook error: " + t);
                    }
                }
            });
            Logx.i("hooked Activity.dispatchActivityResult");
        } catch (Throwable t) {
            Logx.i("dispatch hook failed: " + t);
        }
    }

    private void onDispatch(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) param.thisObject;
        int first = -1;
        int second = -1;
        int intentIndex = -1;
        for (int i = 0; i < param.args.length; i++) {
            Object arg = param.args[i];
            if (arg instanceof Integer) {
                if (first < 0) {
                    first = i;
                } else if (second < 0) {
                    second = i;
                }
            } else if (arg instanceof Intent && intentIndex < 0) {
                intentIndex = i;
            }
        }
        if (first < 0) {
            return;
        }
        int primary = (Integer) param.args[first];
        int other = second >= 0 ? (Integer) param.args[second] : Activity.RESULT_CANCELED;
        Pending pending = take(activity, primary);
        int resultCode = other;
        int resultIndex = second;
        if (pending == null && second >= 0) {
            pending = take(activity, other);
            if (pending != null) {
                resultCode = primary;
                resultIndex = first;
                Logx.i("matched request code on the second integer");
            }
        }
        if (pending == null) {
            return;
        }
        Intent data = intentIndex >= 0 && param.args[intentIndex] instanceof Intent
                ? (Intent) param.args[intentIndex] : null;
        if (data != null && data.getBooleanExtra(ResultIntents.RESULT_MARKER, false)) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            Logx.i("picker cancelled, injected=" + pending.injected);
            if (pending.injected) {
                param.setResult(null);
            }
            return;
        }
        // Copy off the main thread. Replay the original delivery once paths exist.
        // setResult skips the original call, so the import must be scheduled even if
        // the progress overlay cannot be attached.
        param.setResult(null);
        try {
            ImportOverlay.show(activity);
        } catch (Throwable t) {
            Logx.i("overlay failed: " + t);
        }
        Object[] snapshot = param.args.clone();
        Member method = param.method;
        Pending job = pending;
        int resultSlot = resultIndex;
        int intentSlot = intentIndex;
        IO.execute(() -> finishImport(activity, job, data, snapshot, method, resultSlot, intentSlot));
    }

    private void finishImport(
            Activity activity,
            Pending job,
            Intent data,
            Object[] snapshot,
            Member method,
            int resultSlot,
            int intentSlot
    ) {
        long started = System.currentTimeMillis();
        List<MediaImporter.Imported> imported;
        try {
            imported = MediaImporter.importAll(activity, data);
        } catch (Throwable t) {
            Logx.i("import crashed: " + t);
            imported = new ArrayList<>();
        }
        if (job.launch.maxCount > 0 && imported.size() > job.launch.maxCount) {
            Logx.i("trimming selection " + imported.size() + " to max " + job.launch.maxCount);
            imported = new ArrayList<>(imported.subList(0, job.launch.maxCount));
        }
        int videos = 0;
        int images = 0;
        for (MediaImporter.Imported file : imported) {
            if (file.video) {
                videos++;
            } else {
                images++;
            }
        }
        boolean singleVideo = videos == 1 && images == 0;
        List<WeChatResultContract.Item> items = new ArrayList<>();
        for (MediaImporter.Imported file : imported) {
            String thumb = null;
            String hash = file.md5;
            if (file.video && singleVideo) {
                thumb = MediaImporter.videoThumb(activity, file.path);
                if (hash == null) {
                    hash = MediaImporter.md5(file.path);
                }
            }
            items.add(new WeChatResultContract.Item(file.video, file.path, thumb, hash));
        }
        Map<String, Object> extras = WeChatResultContract.build(items, job.launch.sendRaw, job.launch.passthrough);
        Intent rewritten = extras == null ? null : ResultIntents.toIntent(extras);
        Logx.i("import done in " + (System.currentTimeMillis() - started) + "ms images=" + images + " videos=" + videos);
        activity.runOnUiThread(() -> deliver(activity, job, rewritten, snapshot, method, resultSlot, intentSlot));
    }

    private void deliver(
            Activity activity,
            Pending job,
            Intent rewritten,
            Object[] snapshot,
            Member method,
            int resultSlot,
            int intentSlot
    ) {
        try {
            ImportOverlay.hide(activity);
            if (activity.isDestroyed()) {
                Logx.i("activity destroyed before result delivery");
                return;
            }
            if (job.injected) {
                if (rewritten == null) {
                    toast(activity, "沒有匯入到可讀的照片或影片");
                    return;
                }
                String how = GalleryHandoff.deliver(activity, rewritten);
                Logx.i("delivery=" + how);
                return;
            }
            if (resultSlot < 0 || intentSlot < 0) {
                Logx.i("cannot replay result, missing argument slots");
                return;
            }
            Object[] replay = snapshot.clone();
            if (rewritten == null) {
                replay[resultSlot] = Activity.RESULT_CANCELED;
                replay[intentSlot] = null;
                toast(activity, "沒有匯入到可讀的照片或影片");
            } else {
                replay[resultSlot] = Activity.RESULT_OK;
                replay[intentSlot] = rewritten;
            }
            BYPASS.set(Boolean.TRUE);
            try {
                XposedBridge.invokeOriginalMethod(method, activity, replay);
                Logx.i("delivery=activity-result replayed album result to " + activity.getClass().getName());
            } catch (Throwable t) {
                Logx.i("replay failed: " + t);
            } finally {
                BYPASS.remove();
            }
        } catch (Throwable t) {
            Logx.i("deliver failed: " + t);
        }
    }

    private void hookResume() {
        try {
            XposedBridge.hookAllMethods(Instrumentation.class, "callActivityOnResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = null;
                    for (Object arg : param.args) {
                        if (arg instanceof Activity) {
                            activity = (Activity) arg;
                            break;
                        }
                    }
                    if (activity == null) {
                        return;
                    }
                    try {
                        maybeInject(activity);
                    } catch (Throwable t) {
                        Logx.i("button inject error: " + t);
                    }
                }
            });
            Logx.i("hooked callActivityOnResume");
        } catch (Throwable t) {
            Logx.i("resume hook failed: " + t);
        }
    }

    private void maybeInject(Activity activity) {
        if (!PickerClassMatcher.isPickerClass(activity.getClass().getName(), DISCOVERED)) {
            return;
        }
        ModulePrefs prefs = ModulePrefs.load();
        if (activity.getWindow() == null) {
            return;
        }
        if (!prefs.showButton) {
            AlbumButton.detach(activity);
            return;
        }
        activity.getWindow().getDecorView().post(() -> {
            try {
                AlbumButton.attach(activity, view -> launchFromButton(activity));
            } catch (Throwable t) {
                Logx.i("attach button failed: " + t);
            }
        });
    }

    private void launchFromButton(Activity activity) {
        ModulePrefs prefs = ModulePrefs.load();
        LaunchOptions launch = LaunchOptionsReader.fromIntent(activity.getIntent(), prefs.forceRaw);
        Intent photos = SystemPickerIntents.build(activity, launch);
        remember(activity, INJECT_RC, new Pending(launch, true));
        if (startForResult(activity, photos)) {
            Logx.i("button launched Google Photos external picker");
            return;
        }
        Intent hyper = SystemPickerIntents.buildHyperFallback(activity, launch);
        if (hyper != null && startForResult(activity, hyper)) {
            Logx.i("button fell back to Hyper ACTION_PICK_IMAGES");
            return;
        }
        take(activity, INJECT_RC);
        toast(activity, "無法開啟 Google 相簿");
    }

    private static boolean startForResult(Activity activity, Intent picker) {
        if (activity == null || picker == null) {
            return false;
        }
        try {
            activity.startActivityForResult(picker, INJECT_RC);
            return true;
        } catch (Throwable t) {
            Logx.i("picker launch failed: " + t);
            return false;
        }
    }

    private void scheduleDiscover(String process) {
        if (!shouldScan(process)) {
            Logx.i("dexkit skipped for " + process);
            return;
        }
        Runnable job = () -> {
            if (!SCANNED.compareAndSet(false, true)) {
                return;
            }
            Application app = android.app.AndroidAppHelper.currentApplication();
            if (app == null) {
                SCANNED.set(false);
                return;
            }
            DexKitGalleryFinder.discover(app, DISCOVERED);
        };
        if (android.app.AndroidAppHelper.currentApplication() != null) {
            IO.execute(job);
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    IO.execute(job);
                }
            });
        } catch (Throwable t) {
            Logx.i("Application.onCreate hook failed: " + t);
        }
    }

    private static boolean shouldScan(String process) {
        if (process == null || TARGET.equals(process)) {
            return true;
        }
        return process.contains(":tools") || process.endsWith(":support");
    }

    private static String processName() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                String name = Application.getProcessName();
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            String name = android.app.AndroidAppHelper.currentProcessName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Throwable ignored) {
        }
        return TARGET;
    }

    private static Activity findActivity(Object[] args) {
        Activity found = null;
        for (Object arg : args) {
            if (arg instanceof Activity) {
                found = (Activity) arg;
            }
        }
        if (found != null) {
            return found;
        }
        for (Object arg : args) {
            if (arg instanceof Context) {
                Activity unwrapped = unwrap((Context) arg);
                if (unwrapped != null) {
                    return unwrapped;
                }
            }
        }
        return null;
    }

    private static Activity unwrap(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == null || next == context) {
                break;
            }
            context = next;
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    private static int requestCodeAfter(Object[] args, int intentIndex) {
        if (intentIndex >= 0 && intentIndex + 1 < args.length && args[intentIndex + 1] instanceof Integer) {
            return (Integer) args[intentIndex + 1];
        }
        for (int i = Math.max(intentIndex + 1, 0); i < args.length; i++) {
            if (args[i] instanceof Integer) {
                return (Integer) args[i];
            }
        }
        return -1;
    }

    private static void remember(Activity activity, int requestCode, Pending pending) {
        synchronized (LOCK) {
            Map<Integer, Pending> map = PENDING.get(activity);
            if (map == null) {
                map = new HashMap<>();
                PENDING.put(activity, map);
            }
            map.put(requestCode, pending);
        }
    }

    private static Pending take(Activity activity, int requestCode) {
        synchronized (LOCK) {
            Map<Integer, Pending> map = PENDING.get(activity);
            if (map == null) {
                return null;
            }
            Pending pending = map.remove(requestCode);
            if (map.isEmpty()) {
                PENDING.remove(activity);
            }
            return pending;
        }
    }

    private static void toast(Context context, String message) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Logx.i("toast failed: " + t);
        }
    }
}
