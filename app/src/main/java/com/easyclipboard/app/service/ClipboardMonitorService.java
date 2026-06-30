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

import com.easyclipboard.app.R;
import com.easyclipboard.app.data.ClipRepository;

/**
 * Foreground service that listens for primary-clip changes and feeds them into
 * {@link ClipRepository}. This is the no-root capture path: it works while the
 * service is alive (app foreground / IME up), within the OS background-clipboard
 * access limits. START_STICKY so the OS restarts it.
 */
public class ClipboardMonitorService extends Service {

    private static final String CHANNEL_ID = "clipboard_monitor";
    private static final int NOTIFICATION_ID = 1001;

    private ClipboardManager clipboardManager;
    private ClipRepository repo;

    private final ClipboardManager.OnPrimaryClipChangedListener listener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    capture();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        repo = ClipRepository.get(this);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.addPrimaryClipChangedListener(listener);
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
        if (clipboardManager != null) {
            clipboardManager.removePrimaryClipChangedListener(listener);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
