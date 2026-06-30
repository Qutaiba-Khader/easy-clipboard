package com.easyclipboard.app;

import android.app.Application;
import android.os.Build;

import com.easyclipboard.app.data.ClipRepository;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * Application entry point.
 *
 * - On Android P+ the process is normally forbidden from reflecting on hidden
 *   framework classes like {@code android.content.IClipboard}. Exempting hidden
 *   APIs here (once, at process start) is REQUIRED for the Shizuku global-capture
 *   path. Fully guarded; never affects normal operation.
 * - Kicks off the one-time background load of the clip history so the in-memory
 *   cache is warm before the user ever opens the panel (no main-thread disk I/O
 *   on the show path).
 */
public class EasyClipApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        // Warm the in-memory clip cache on a background thread.
        try {
            ClipRepository.get(getApplicationContext());
        } catch (Throwable ignored) {
            // never block app startup
        }
    }
}
