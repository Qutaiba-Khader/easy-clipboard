package com.easyclipboard.app.shizuku;

import android.content.ClipData;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.easyclipboard.app.data.ClipRepository;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * Optional privileged backend via Shizuku. Fully ISOLATED: every call is guarded
 * so the rest of the app works whether or not Shizuku is installed/running.
 *
 * When Shizuku is running and granted, {@link #startGlobalCapture(Context)} talks
 * to the framework clipboard service through Shizuku's binder wrapper, so calls
 * execute as the shell UID ("com.android.shell") which the OS lets read the
 * clipboard freely. A change-listener is registered at the framework level, so
 * EVERY copy from ANY app is recorded in the background (no IME/foreground UI
 * needed), as long as the host process stays alive (kept up by the foreground
 * {@code ClipboardMonitorService}).
 *
 * Method signatures of IClipboard differ across Android 8..15 (extra String
 * attributionTag, int userId, int deviceId params were added over time), so
 * arguments are built reflectively from each Method's parameter types.
 *
 * The static {@code appContext} held here is always an application context
 * (never an Activity), so it does not leak a UI lifecycle.
 */
public final class ShizukuClipboardManager {

    public static final int REQUEST_CODE = 4711;
    private static final String TAG = "EasyClipShizuku";
    private static final String SHELL_PKG = "com.android.shell";
    private static final long POLL_INTERVAL_MS = 2000L;

    private static volatile boolean capturing = false;
    private static Object iClipboard;
    private static Object changeListener; // android.content.IOnPrimaryClipChangedListener.Stub
    private static Context appContext;
    private static String lastText;

    private static HandlerThread pollThread;
    private static Handler pollHandler;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ShizukuClipboardManager() {
    }

    // ---- availability / permission --------------------------------------

    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                return false;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission() {
        try {
            if (Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE);
            }
        } catch (Throwable t) {
            Log.w(TAG, "requestPermission failed", t);
        }
    }

    public static boolean isCapturing() {
        return capturing;
    }

    // ---- global capture --------------------------------------------------

    /**
     * Start real global background clipboard capture via Shizuku. Idempotent and
     * fully guarded; returns true if capture is active afterwards.
     */
    public static synchronized boolean startGlobalCapture(Context ctx) {
        if (capturing) {
            return true;
        }
        if (ctx == null || !hasPermission()) {
            Log.w(TAG, "startGlobalCapture: no Shizuku permission");
            return false;
        }
        appContext = ctx.getApplicationContext();
        try {
            iClipboard = buildClipboardInterface();
            if (iClipboard == null) {
                Log.w(TAG, "startGlobalCapture: could not obtain IClipboard");
                return false;
            }

            changeListener = new android.content.IOnPrimaryClipChangedListener.Stub() {
                @Override
                public void dispatchPrimaryClipChanged() {
                    onClipChanged();
                }
            };

            boolean registered = registerListener();
            if (registered) {
                capturing = true;
                Log.i(TAG, "Global capture ON (listener mode)");
                onClipChanged(); // grab whatever is currently on the clipboard
                return true;
            }

            // Fallback: poll the clipboard on a background thread.
            startPolling();
            capturing = true;
            Log.i(TAG, "Global capture ON (poll mode)");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "startGlobalCapture failed", t);
            capturing = false;
            return false;
        }
    }

    public static synchronized void stopGlobalCapture() {
        try {
            if (changeListener != null && iClipboard != null) {
                Method remove = findMethod(iClipboard, "removePrimaryClipChangedListener");
                if (remove != null) {
                    remove.invoke(iClipboard, buildArgs(remove, changeListener));
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "stopGlobalCapture: remove listener failed", t);
        }
        stopPolling();
        capturing = false;
        changeListener = null;
        iClipboard = null;
        lastText = null;
        Log.i(TAG, "Global capture OFF");
    }

    // ---- internals -------------------------------------------------------

    private static Object buildClipboardInterface() throws Exception {
        android.os.IBinder b = SystemServiceHelper.getSystemService("clipboard");
        if (b == null) {
            return null;
        }
        android.os.IBinder wrapped = new ShizukuBinderWrapper(b);
        Class<?> stub = Class.forName("android.content.IClipboard$Stub");
        Method asInterface = stub.getMethod("asInterface", android.os.IBinder.class);
        return asInterface.invoke(null, wrapped);
    }

    private static boolean registerListener() {
        try {
            Method add = findMethod(iClipboard, "addPrimaryClipChangedListener");
            if (add == null) {
                return false;
            }
            add.invoke(iClipboard, buildArgs(add, changeListener));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "addPrimaryClipChangedListener failed; will poll", t);
            return false;
        }
    }

    private static void onClipChanged() {
        try {
            ClipData data = readPrimaryClip();
            if (data == null || data.getItemCount() == 0) {
                return;
            }
            CharSequence cs = data.getItemAt(0).coerceToText(appContext);
            if (cs == null) {
                return;
            }
            final String text = cs.toString();
            if (text.isEmpty() || ClipRepository.SENTINEL.equals(text) || text.equals(lastText)) {
                return;
            }
            lastText = text;
            final Context ctx = appContext;
            if (ctx == null) {
                return;
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    ClipRepository.get(ctx).addText(text, ctx);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "onClipChanged failed", t);
        }
    }

    private static ClipData readPrimaryClip() throws Exception {
        Method get = findMethod(iClipboard, "getPrimaryClip");
        if (get == null) {
            return null;
        }
        Object result = get.invoke(iClipboard, buildArgs(get, null));
        return (result instanceof ClipData) ? (ClipData) result : null;
    }

    private static void startPolling() {
        stopPolling();
        pollThread = new HandlerThread("EasyClipPoll");
        pollThread.start();
        pollHandler = new Handler(pollThread.getLooper());
        pollHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!capturing) {
                    return;
                }
                onClipChanged();
                Handler h = pollHandler;
                if (h != null) {
                    h.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        });
    }

    private static void stopPolling() {
        if (pollHandler != null) {
            pollHandler.removeCallbacksAndMessages(null);
            pollHandler = null;
        }
        if (pollThread != null) {
            pollThread.quitSafely();
            pollThread = null;
        }
    }

    private static Method findMethod(Object target, String name) {
        if (target == null) {
            return null;
        }
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Build the argument array for a clipboard method from its parameter types:
     *   - IOnPrimaryClipChangedListener param -> the supplied listener (add/remove),
     *   - first String -> shell package "com.android.shell"; further String -> null,
     *   - int (userId, then deviceId on API 34+) -> 0.
     */
    private static Object[] buildArgs(Method m, Object listener) {
        Class<?>[] types = m.getParameterTypes();
        Object[] args = new Object[types.length];
        boolean firstString = true;
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t.getName().equals("android.content.IOnPrimaryClipChangedListener")) {
                args[i] = listener;
            } else if (t == String.class) {
                args[i] = firstString ? SHELL_PKG : null;
                firstString = false;
            } else if (t == int.class || t == Integer.TYPE) {
                args[i] = 0; // userId, then deviceId default
            } else {
                args[i] = null;
            }
        }
        return args;
    }
}
