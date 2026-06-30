package com.easyclipboard.app.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.easyclipboard.app.R;
import com.easyclipboard.app.service.ClipboardAccessibilityService;
import com.easyclipboard.app.service.ClipboardMonitorService;
import com.easyclipboard.app.shizuku.ShizukuClipboardManager;

/**
 * Launch / Setup screen (first tab). Lists every capability the app needs as a
 * row with title + subtitle + a clear enabled/not-enabled status and a Fix/Enable
 * button that opens the right system screen. Statuses are recomputed in
 * onResume() so they refresh when the user returns from Settings.
 */
public class SetupFragment extends Fragment {

    private static final int GREEN = 0xFF2E7D32;
    private static final int RED = 0xFFC62828;
    private static final int AMBER = 0xFFEF6C00;

    private LinearLayout container;
    private ActivityResultLauncher<String> notifPermLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> rebuild());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setup, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        container = view.findViewById(R.id.setup_container);
        rebuild();
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        if (container == null || getContext() == null) {
            return;
        }
        container.removeAllViews();
        addHeader();
        addNotificationRow();
        addImeRow();
        addAccessibilityRow();
        addOverlayRow();
        addMonitorRow();
        addShizukuRow();
    }

    // ---- capability rows -------------------------------------------------

    private void addNotificationRow() {
        boolean enabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        addRow(getString(R.string.setup_notif_title),
                getString(R.string.setup_notif_sub),
                enabled ? Status.OK : Status.BAD,
                getString(R.string.setup_grant),
                () -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    } else {
                        openAppNotificationSettings();
                    }
                });
    }

    private void addImeRow() {
        boolean enabled = isImeEnabled();
        addRow(getString(R.string.setup_ime_title),
                getString(R.string.setup_ime_sub),
                enabled ? Status.OK : Status.BAD,
                enabled ? getString(R.string.setup_switch) : getString(R.string.setup_enable),
                () -> {
                    if (enabled) {
                        InputMethodManager imm = (InputMethodManager)
                                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showInputMethodPicker();
                        }
                    } else {
                        safeStart(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                    }
                });
    }

    private void addAccessibilityRow() {
        boolean enabled = isAccessibilityEnabled();
        addRow(getString(R.string.setup_acc_title),
                getString(R.string.setup_acc_sub),
                enabled ? Status.OK : Status.BAD,
                getString(R.string.setup_enable),
                () -> safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    private void addOverlayRow() {
        boolean enabled = Settings.canDrawOverlays(requireContext());
        addRow(getString(R.string.setup_overlay_title),
                getString(R.string.setup_overlay_sub),
                enabled ? Status.OK : Status.BAD,
                getString(R.string.setup_enable),
                () -> safeStart(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + requireContext().getPackageName()))));
    }

    private void addMonitorRow() {
        boolean running = ClipboardMonitorService.isRunning;
        addRow(getString(R.string.setup_monitor_title),
                getString(R.string.setup_monitor_sub),
                running ? Status.OK : Status.BAD,
                running ? getString(R.string.setup_stop) : getString(R.string.setup_start),
                () -> {
                    if (running) {
                        ClipboardMonitorService.stop(requireContext());
                    } else {
                        ClipboardMonitorService.start(requireContext());
                    }
                    container.postDelayed(this::rebuild, 400);
                });
    }

    private void addShizukuRow() {
        boolean available = ShizukuClipboardManager.isShizukuAvailable();
        boolean granted = ShizukuClipboardManager.hasPermission();
        Status status = !available ? Status.OPTIONAL : (granted ? Status.OK : Status.BAD);
        String subtitle = available
                ? getString(R.string.setup_shizuku_sub)
                : getString(R.string.setup_shizuku_optional);
        addRow(getString(R.string.setup_shizuku_title),
                subtitle,
                status,
                available ? getString(R.string.setup_request) : getString(R.string.setup_info),
                () -> {
                    if (available) {
                        ShizukuClipboardManager.requestPermission();
                        container.postDelayed(this::rebuild, 400);
                    } else {
                        Toast.makeText(requireContext(),
                                R.string.setup_shizuku_optional, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---- checks ----------------------------------------------------------

    private boolean isImeEnabled() {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return false;
        }
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (info.getId() != null && info.getId().contains("ClipboardImeService")) {
                return true;
            }
        }
        return false;
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        String svc = requireContext().getPackageName() + "/"
                + ClipboardAccessibilityService.class.getName();
        return enabled.contains(svc) || enabled.contains("ClipboardAccessibilityService");
    }

    // ---- helpers ---------------------------------------------------------

    private enum Status { OK, BAD, OPTIONAL }

    private void addHeader() {
        TextView header = new TextView(requireContext());
        header.setText(R.string.setup_header);
        header.setTextSize(16);
        header.setPadding(dp(4), dp(4), dp(4), dp(12));
        container.addView(header);
    }

    private void addRow(String title, String subtitle, Status status,
                        String buttonLabel, final Runnable action) {
        Context ctx = requireContext();

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = dp(10);
        row.setPadding(pad, pad, pad, pad);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);

        TextView subView = new TextView(ctx);
        subView.setText(subtitle);
        subView.setTextSize(13);

        TextView statusView = new TextView(ctx);
        switch (status) {
            case OK:
                statusView.setText(R.string.setup_status_ok);
                statusView.setTextColor(GREEN);
                break;
            case OPTIONAL:
                statusView.setText(R.string.setup_status_optional);
                statusView.setTextColor(AMBER);
                break;
            default:
                statusView.setText(R.string.setup_status_bad);
                statusView.setTextColor(RED);
                break;
        }
        statusView.setTextSize(13);

        texts.addView(titleView);
        texts.addView(subView);
        texts.addView(statusView);

        Button button = new Button(ctx);
        button.setText(buttonLabel);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });

        row.addView(texts);
        row.addView(button);
        container.addView(row);

        View divider = new View(ctx);
        divider.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0x22000000);
        container.addView(divider);
    }

    private void openAppNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        safeStart(intent);
    }

    private void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
