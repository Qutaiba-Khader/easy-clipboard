package com.easyclipboard.app;

import android.app.Application;
import android.os.Build;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * Application entry point. On Android P+ the process is normally forbidden from
 * reflecting on hidden framework classes like {@code android.content.IClipboard}.
 * Exempting all hidden APIs here (once, at process start) is REQUIRED for the
 * Shizuku global-capture path to reflect on the clipboard service interface.
 *
 * This is fully guarded and never affects normal (non-Shizuku) operation.
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
    }
}
