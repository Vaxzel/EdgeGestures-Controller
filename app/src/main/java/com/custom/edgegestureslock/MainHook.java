package com.custom.edgegestureslock;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import com.custom.edgegestureslock.model.RuleModel;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TARGET_PKG = "com.ss.edgegestures";
    private static final String ACTION_RELOAD_RULES = "com.custom.edgegestureslock.ACTION_RELOAD_RULES";

    private static volatile String sCurrentForegroundPackage = "";
    private static final Map<String, RuleModel> sRules = new ConcurrentHashMap<>();
    private static volatile boolean sReceiverRegistered = false;
    private static final List<WeakReference<View>> sActiveViews = new CopyOnWriteArrayList<>();
    private static volatile WeakReference<Service> sEdgeServiceInstance = null;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[EdgeGesturesLock] Active in package: " + lpparam.packageName);

        // 1. Anti-Crash: Tangkap uncaught exception
        try {
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    XposedBridge.log("[EdgeGesturesLock] Intercepted crash in thread " + t.getName() + ": " + e);
                }
            });
            XposedBridge.log("[EdgeGesturesLock] Anti-crash handler installed");
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error setting crash handler: " + t);
        }

        // 2. Anti-Suicide: Cegah aplikasi menutup dirinya sendiri
        try {
            XposedHelpers.findAndHookMethod(
                Process.class,
                "killProcess",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int pid = (Integer) param.args[0];
                        if (pid == Process.myPid()) {
                            XposedBridge.log("[EdgeGesturesLock] Blocked suicide attempt via killProcess!");
                            param.setResult(null);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookMethod(
                System.class,
                "exit",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("[EdgeGesturesLock] Blocked suicide attempt via System.exit!");
                        param.setResult(null);
                    }
                }
            );
        } catch (Throwable ignored) {}

        // 3. Prioritas thread
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
        } catch (Throwable ignored) {}

        // 4. Hook KeyguardManager (Layar Kunci / Kamera)
        try {
            XposedHelpers.findAndHookMethod(
                KeyguardManager.class,
                "isKeyguardLocked",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(false);
                    }
                }
            );
            XposedBridge.log("[EdgeGesturesLock] Hooked isKeyguardLocked -> false");
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error hooking isKeyguardLocked: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                KeyguardManager.class,
                "inKeyguardRestrictedInputMode",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(false);
                    }
                }
            );
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookMethod(
                KeyguardManager.class,
                "isDeviceLocked",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(false);
                    }
                }
            );
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookMethod(
                KeyguardManager.class,
                "isDeviceLocked",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(false);
                    }
                }
            );
        } catch (Throwable ignored) {}

        // 5. Injeksi FLAG_SHOW_WHEN_LOCKED pada WindowManager & Tracking Active Views
        try {
            XposedHelpers.findAndHookMethod(
                "android.view.WindowManagerImpl",
                lpparam.classLoader,
                "addView",
                View.class,
                ViewGroup.LayoutParams.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View v = (View) param.args[0];
                        Object p = param.args[1];
                        if (p instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) p;
                            lp.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
                        }
                        if (v != null && isEdgeView(v)) {
                            registerActiveView(v);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookMethod(
                "android.view.WindowManagerImpl",
                lpparam.classLoader,
                "updateViewLayout",
                View.class,
                ViewGroup.LayoutParams.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View v = (View) param.args[0];
                        Object p = param.args[1];
                        if (p instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) p;
                            lp.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
                        }
                        if (v != null && isEdgeView(v)) {
                            registerActiveView(v);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}

        // 6. Inisialisasi Service & Aturan (EdgeService)
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Service",
                lpparam.classLoader,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if ("com.ss.edgegestures.EdgeService".equals(param.thisObject.getClass().getName())) {
                            final Service s = (Service) param.thisObject;
                            sEdgeServiceInstance = new WeakReference<>(s);
                            loadRules(s);
                            registerReloadReceiver(s);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error hooking Service.onCreate: " + t);
        }

        // 7. Native Edge Control: Hook tp1.b(Context, String, boolean)
        try {
            XposedHelpers.findAndHookMethod(
                "tp1",
                lpparam.classLoader,
                "b",
                Context.class,
                String.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[1];
                        if (key == null) return;

                        String fg = sCurrentForegroundPackage;
                        if (fg == null || fg.isEmpty()) return;

                        RuleModel rule = sRules.get(fg);
                        if (rule == null || !rule.hasActiveRules()) return;

                        if ("enableLeft".equals(key) && rule.disableLeft) {
                            param.setResult(false);
                            XposedBridge.log("[EdgeGesturesLock] Native disable: enableLeft for " + fg);
                        } else if ("enableRight".equals(key) && rule.disableRight) {
                            param.setResult(false);
                            XposedBridge.log("[EdgeGesturesLock] Native disable: enableRight for " + fg);
                        } else if ("enableBottom".equals(key) && rule.disableBottom) {
                            param.setResult(false);
                            XposedBridge.log("[EdgeGesturesLock] Native disable: enableBottom for " + fg);
                        }
                    }
                }
            );
            XposedBridge.log("[EdgeGesturesLock] Hooked tp1.b for native edge toggling");
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error hooking tp1.b: " + t);
        }

        // 8. Deteksi Foreground App via EdgeService internal & AccessibilityEvent
        try {
            XposedHelpers.findAndHookMethod(
                "com.ss.edgegestures.EdgeService",
                lpparam.classLoader,
                "onAccessibilityEvent",
                AccessibilityEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof Service) {
                            if (sEdgeServiceInstance == null) {
                                sEdgeServiceInstance = new WeakReference<>((Service) param.thisObject);
                            }
                            registerReloadReceiver((Context) param.thisObject);
                        }
                        AccessibilityEvent ev = (AccessibilityEvent) param.args[0];
                        if (ev != null) {
                            int eventType = ev.getEventType();
                            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                                CharSequence pkg = ev.getPackageName();
                                if (pkg != null) {
                                    String pkgStr = pkg.toString();
                                    if (!pkgStr.equals("com.ss.edgegestures") &&
                                        !pkgStr.equals("android") &&
                                        !pkgStr.equals("com.android.systemui")) {
                                        if (!pkgStr.equals(sCurrentForegroundPackage)) {
                                            sCurrentForegroundPackage = pkgStr;
                                            XposedBridge.log("[EdgeGesturesLock] Foreground changed (a11y): " + pkgStr);
                                            if (param.thisObject instanceof Context) {
                                                loadRules((Context) param.thisObject);
                                            }
                                            applyRulesToViews();
                                            refreshEdgeService();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            );
            XposedBridge.log("[EdgeGesturesLock] Hooked onAccessibilityEvent for active package tracking");
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error hooking onAccessibilityEvent: " + t);
        }

        // Hook EdgeService.m(boolean) yang bertugas update overlay state
        try {
            XposedHelpers.findAndHookMethod(
                "com.ss.edgegestures.EdgeService",
                lpparam.classLoader,
                "m",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof Service) {
                            if (sEdgeServiceInstance == null) {
                                sEdgeServiceInstance = new WeakReference<>((Service) param.thisObject);
                            }
                            registerReloadReceiver((Context) param.thisObject);
                        }
                        try {
                            Field fieldN = param.thisObject.getClass().getDeclaredField("n");
                            fieldN.setAccessible(true);
                            Object comp = fieldN.get(null);
                            if (comp instanceof ComponentName) {
                                String pkg = ((ComponentName) comp).getPackageName();
                                if (pkg != null && !pkg.isEmpty() && !pkg.equals("com.ss.edgegestures") && !pkg.equals(sCurrentForegroundPackage)) {
                                    sCurrentForegroundPackage = pkg;
                                    XposedBridge.log("[EdgeGesturesLock] Foreground from EdgeService.n: " + pkg);
                                    if (param.thisObject instanceof Context) {
                                        loadRules((Context) param.thisObject);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                        applyRulesToViews();
                    }
                }
            );
            XposedBridge.log("[EdgeGesturesLock] Hooked EdgeService.m for layout & state sync");
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error hooking EdgeService.m: " + t);
        }

        // 9. Hook ge0.onAttachedToWindow untuk mendaftarkan edge view
        try {
            XposedHelpers.findAndHookMethod(
                "ge0",
                lpparam.classLoader,
                "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View v = (View) param.thisObject;
                        registerActiveView(v);
                        applyRulesToViews();
                    }
                }
            );
            XposedBridge.log("[EdgeGesturesLock] Hooked ge0.onAttachedToWindow");
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error hooking ge0.onAttachedToWindow: " + t);
        }

        // 10. Hook Touch Event untuk Bypass Per-Sisi (fe0.onTouch)
        try {
            XposedHelpers.findAndHookMethod(
                "fe0",
                lpparam.classLoader,
                "onTouch",
                View.class,
                MotionEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String fg = sCurrentForegroundPackage;
                        if (fg == null || fg.isEmpty()) return;

                        RuleModel rule = sRules.get(fg);
                        if (rule == null || !rule.hasActiveRules()) return;

                        View v = (View) param.args[0];
                        String side = detectViewSide(v);
                        if (side != null) {
                            if (("bottom".equals(side) && rule.disableBottom) ||
                                ("left".equals(side) && rule.disableLeft) ||
                                ("right".equals(side) && rule.disableRight)) {
                                param.setResult(false);
                            }
                        }
                    }
                }
            );
            XposedBridge.log("[EdgeGesturesLock] Hooked fe0.onTouch for per-side bypass");
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error hooking fe0.onTouch: " + t);
        }

        // 11. Hook dispatchTouchEvent pada ge0 sebagai perlindungan lapis kedua
        try {
            XposedHelpers.findAndHookMethod(
                "ge0",
                lpparam.classLoader,
                "dispatchTouchEvent",
                MotionEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String fg = sCurrentForegroundPackage;
                        if (fg == null || fg.isEmpty()) return;

                        RuleModel rule = sRules.get(fg);
                        if (rule == null || !rule.hasActiveRules()) return;

                        View v = (View) param.thisObject;
                        String side = detectViewSide(v);
                        if (side != null) {
                            if (("bottom".equals(side) && rule.disableBottom) ||
                                ("left".equals(side) && rule.disableLeft) ||
                                ("right".equals(side) && rule.disableRight)) {
                                param.setResult(false);
                            }
                        }
                    }
                }
            );
            XposedBridge.log("[EdgeGesturesLock] Hooked ge0.dispatchTouchEvent for per-side bypass");
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error hooking ge0.dispatchTouchEvent: " + t);
        }
    }

    private static boolean isEdgeView(View v) {
        if (v == null) return false;
        String name = v.getClass().getName();
        return name.equals("ge0") || name.endsWith(".ge0") || name.contains("EdgeView");
    }

    private static void registerActiveView(View v) {
        if (v == null) return;
        for (WeakReference<View> ref : sActiveViews) {
            if (ref.get() == v) return;
        }
        sActiveViews.add(new WeakReference<>(v));
        XposedBridge.log("[EdgeGesturesLock] Registered active edge view (Total: " + sActiveViews.size() + ")");
    }

    public static void refreshEdgeService() {
        try {
            if (sEdgeServiceInstance != null) {
                final Service s = sEdgeServiceInstance.get();
                if (s != null) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Method mMethod = s.getClass().getDeclaredMethod("m", boolean.class);
                                mMethod.setAccessible(true);
                                mMethod.invoke(s, false);
                                XposedBridge.log("[EdgeGesturesLock] Refreshed EdgeService native edge states");
                            } catch (Throwable t) {
                                XposedBridge.log("[EdgeGesturesLock] Error calling EdgeService.m: " + t);
                            }
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void registerReloadReceiver(final Context ctx) {
        if (sReceiverRegistered || ctx == null) return;
        try {
            IntentFilter filter = new IntentFilter(ACTION_RELOAD_RULES);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    XposedBridge.log("[EdgeGesturesLock] Received ACTION_RELOAD_RULES broadcast");
                    String rulesJson = intent.getStringExtra("rules_json");
                    if (rulesJson != null && !rulesJson.trim().isEmpty()) {
                        parseJsonRules(rulesJson);
                    } else {
                        loadRules(context);
                    }
                    applyRulesToViews();
                    refreshEdgeService();
                }
            };

            boolean registered = false;
            try {
                // Android 14+ (Context.RECEIVER_EXPORTED = 2)
                Method regMethod = Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, int.class);
                regMethod.invoke(ctx, receiver, filter, 2);
                registered = true;
                XposedBridge.log("[EdgeGesturesLock] Registered reload receiver with RECEIVER_EXPORTED");
            } catch (Throwable ignored) {}

            if (!registered) {
                try {
                    ctx.registerReceiver(receiver, filter);
                    XposedBridge.log("[EdgeGesturesLock] Registered reload receiver (standard)");
                } catch (Throwable t) {
                    XposedBridge.log("[EdgeGesturesLock] Error registering reload receiver: " + t);
                }
            }
            sReceiverRegistered = true;
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error in registerReloadReceiver: " + t);
        }
    }

    public static void applyRulesToViews() {
        String fg = sCurrentForegroundPackage;
        final RuleModel rule = (fg != null && !fg.isEmpty()) ? sRules.get(fg) : null;

        java.util.List<WeakReference<View>> deadRefs = new java.util.ArrayList<>();
        for (WeakReference<View> ref : sActiveViews) {
            final View v = ref.get();
            if (v == null) {
                deadRefs.add(ref);
                continue;
            }

            v.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        String side = detectViewSide(v);
                        if (side == null) return;

                        boolean shouldDisable = false;
                        if (rule != null) {
                            if ("bottom".equals(side) && rule.disableBottom) shouldDisable = true;
                            else if ("left".equals(side) && rule.disableLeft) shouldDisable = true;
                            else if ("right".equals(side) && rule.disableRight) shouldDisable = true;
                        }

                        ViewGroup.LayoutParams lp = v.getLayoutParams();
                        if (lp instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) lp;
                            int originalFlags = wlp.flags;
                            int originalVisibility = v.getVisibility();
                            if (shouldDisable) {
                                wlp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                                v.setVisibility(View.GONE);
                            } else {
                                wlp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                                v.setVisibility(View.VISIBLE);
                            }

                            if (originalFlags != wlp.flags || originalVisibility != v.getVisibility()) {
                                Context ctx = v.getContext();
                                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                                if (wm != null) {
                                    wm.updateViewLayout(v, wlp);
                                    XposedBridge.log("[EdgeGesturesLock] Updated layout flags for side '" + side + "': disabled=" + shouldDisable);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("[EdgeGesturesLock] Error updating view layout: " + t);
                    }
                }
            });
        }
        if (!deadRefs.isEmpty()) {
            sActiveViews.removeAll(deadRefs);
        }
    }

    private static String detectViewSide(View v) {
        if (v == null) return null;

        // 1. Cek field 'i' pada ge0 (3=LEFT, 5=RIGHT, 80=BOTTOM)
        try {
            Field fieldI = v.getClass().getDeclaredField("i");
            fieldI.setAccessible(true);
            Object iVal = fieldI.get(v);
            if (iVal instanceof Integer) {
                int i = (Integer) iVal;
                if (i == 3) return "left";
                if (i == 5) return "right";
                if (i == 80 || i == 0x50) return "bottom";
            }
        } catch (Throwable ignored) {}

        // 2. Cek Gravity dari WindowManager LayoutParams
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) lp;
            int g = wlp.gravity;
            if ((g & Gravity.BOTTOM) == Gravity.BOTTOM) {
                return "bottom";
            }
            if ((g & Gravity.RIGHT) == Gravity.RIGHT || (g & Gravity.END) == Gravity.END) {
                return "right";
            }
            if ((g & Gravity.LEFT) == Gravity.LEFT || (g & Gravity.START) == Gravity.START) {
                return "left";
            }
        }

        // 3. Fallback orientasi dimensi
        int w = v.getWidth();
        int h = v.getHeight();
        if (w > 0 && h > 0) {
            if (w > h) return "bottom";
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            if (loc[0] > 400) return "right";
            return "left";
        }
        return null;
    }

    private static synchronized void parseJsonRules(String json) {
        if (json == null || json.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject rulesObj = root.optJSONObject("rules");
            if (rulesObj != null) {
                sRules.clear();
                Iterator<String> keys = rulesObj.keys();
                while (keys.hasNext()) {
                    String pkg = keys.next();
                    JSONObject r = rulesObj.optJSONObject(pkg);
                    if (r != null) {
                        RuleModel model = RuleModel.fromJsonObject(r);
                        if (model.hasActiveRules()) {
                            sRules.put(pkg, model);
                        }
                    }
                }
                XposedBridge.log("[EdgeGesturesLock] Parsed rules count: " + sRules.size());
            }
        } catch (Throwable t) {
            XposedBridge.log("[EdgeGesturesLock] Error parsing rules JSON: " + t);
        }
    }

    private static synchronized void loadRules(Context ctx) {
        String json = null;

        // 1. Direct Edge Gestures app internal files directory (No sandbox or permission restrictions)
        if (ctx != null) {
            try {
                File localFile = new File(ctx.getFilesDir(), "edge_rules.json");
                if (localFile.exists() && localFile.canRead()) {
                    json = readFile(localFile);
                }
            } catch (Throwable ignored) {}
        }

        if (json == null || json.isEmpty()) {
            json = readFile(new File("/data/data/com.ss.edgegestures/files/edge_rules.json"));
        }
        if (json == null || json.isEmpty()) {
            json = readFile(new File("/data/user/0/com.ss.edgegestures/files/edge_rules.json"));
        }

        // 2. Fallback /data/local/tmp/edge_rules.json
        if (json == null || json.isEmpty()) {
            json = readFile(new File("/data/local/tmp/edge_rules.json"));
        }

        // 3. Fallback ContentProvider
        if ((json == null || json.isEmpty()) && ctx != null) {
            try {
                Bundle res = ctx.getContentResolver().call(
                    Uri.parse("content://com.custom.edgegestureslock.provider"),
                    "getRules",
                    null,
                    null
                );
                if (res != null && res.containsKey("rules_json")) {
                    json = res.getString("rules_json");
                }
            } catch (Throwable ignored) {}
        }

        // 4. Fallback /sdcard/Download/edge_rules.json
        if (json == null || json.isEmpty()) {
            json = readFile(new File("/sdcard/Download/edge_rules.json"));
        }

        parseJsonRules(json);
    }

    private static String readFile(File file) {
        if (!file.exists() || !file.canRead()) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Throwable e) {
            return null;
        }
    }
}
