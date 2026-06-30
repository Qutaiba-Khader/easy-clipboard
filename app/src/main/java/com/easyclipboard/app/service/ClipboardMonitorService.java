package com.easyclipboard.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.easyclipboard.app.R;
import com.easyclipboard.app.data.ClipRepository;
import com.easyclipboard.app.shizuku.ShizukuClipboardManager;

/**
 * Foreground service that captures clipboard changes into {@link ClipRepository}.
 *
 * Two capture paths:
 *  - the in-process {@link ClipboardManager.OnPrimaryClipChangedListener} (works
 *    while this process is foreground / IME up), and
 *  - if Shizuku is available + granted, the REAL global background capture (every
 *    copy from any app) via {@link ShizukuClipboardManager#startGlobalCapture}.
 *
 * Keeping this as a foreground service keeps the process (and thus the Shizuku
 * in-process listener) alive for true background capture. START_STICKY.
 */
public class ClipboardMonitorService extends Service {

    private static final String CHANNEL_ID = "clipboard_monitor";
    private static final int NOTIFICATION_ID = 1001;

    /** Simple flag the Setup screen reads to show running/stopped. */
    public static volatile boolean isRunning = false;

    private ClipboardManager clipboardManager;
    private ClipRepository repo;

    private final ClipboardManager.OnPrimaryClipChangedListener listener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    capture();
                }
            };

    public static void start(Context ctx) {
        ContextCompat.startForegroundService(ctx,
                new Intent(ctx, ClipboardMonitorService.class));
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, ClipboardMonitorService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        repo = ClipRepository.get(this);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.addPrimaryClipChangedListener(listener);
        }
        // Real global background capture, if Shizuku is running and granted.
        try {
            if (ShizukuClipboardManager.isShizukuAvailable()
                    && ShizukuClipboardManager.hasPermission()) {
                ShizukuClipboardManager.startGlobalCapture(getApplicationContext());
            }
        } catch (Throwable ignored) {
            // Shizuku path is optional; never let it break the service.
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    private void capture() {
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()
                || clipboardManager.getPrimaryClip() == null
                || clipboardManager.getPrimaryClip().getItemCount() == 0) {
            return;
        }
        CharSequence text = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) {
            return;
        }
        String value = text.toString();
        if (ClipRepository.SENTINEL.equals(value)) {
            return;
        }
        repo.addText(value, this);
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.monitor_channel),
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.monitor_running))
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (clipboardManager != null) {
            clipboardManager.removePrimaryClipChangedListener(listener);
        }
        try {
            ShizukuClipboardManager.stopGlobalCapture();
        } catch (Throwable ignored) {
            // no-op
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
