package com.easyclipboard.app.shizuku;

import android.content.pm.PackageManager;

import rikka.shizuku.Shizuku;

/**
 * Optional privileged backend via Shizuku. Fully ISOLATED: every call is guarded
 * so the rest of the app works whether or not Shizuku is installed/running.
 *
 * The real global background capture (binding the framework clipboard service as
 * the shell UID through Shizuku) is left as a documented TODO — the stub compiles
 * and the public probe methods are functional.
 */
public final class ShizukuClipboardManager {

    public static final int REQUEST_CODE = 4711;

    private ShizukuClipboardManager() {
    }

    /** True if a Shizuku binder is alive and reachable. */
    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            // Shizuku not installed / not running / API mismatch.
            return false;
        }
    }

    /** True if the user has already granted this app Shizuku permission. */
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

    /** Prompt the user to grant Shizuku permission. No-op if Shizuku is absent. */
    public static void requestPermission() {
        try {
            if (Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE);
            }
        } catch (Throwable t) {
            // Ignore: feature simply stays unavailable.
        }
    }

    /**
     * TODO: start true global background clipboard capture via Shizuku.
     *
     * Plan: with Shizuku permission granted we run with shell (uid 2000) / root
     * privileges. Bind the framework clipboard service through Shizuku's
     * user-service / IBinder bridge (e.g. obtain IClipboard via
     * Shizuku.getSystemServiceBinder / SystemServiceHelper.getSystemService
     * + Shizuku.wrap), then register a clipboard listener at the framework level
     * so changes are captured even when this app is fully backgrounded and the
     * OS would otherwise deny background clipboard reads. For now this is a stub
     * so the module compiles without a running Shizuku environment.
     *
     * @return false until implemented.
     */
    public static boolean startGlobalCapture() {
        if (!hasPermission()) {
            return false;
        }
        // Intentionally not implemented yet — see method javadoc.
        return false;
    }
}
