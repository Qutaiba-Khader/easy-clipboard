package com.easyclipboard.app.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.easyclipboard.app.R;
import com.easyclipboard.app.data.ClipRepository;
import com.easyclipboard.app.model.Clip;
import com.easyclipboard.app.ui.ClipAdapter;

import java.util.List;

/**
 * Accessibility service that powers the headline feature: double-tapping any
 * text field in ANY app pops a keyboard-style clipboard panel up from the
 * bottom; tapping a clip pastes it into that field.
 *
 * Detection mirrors the original AccessService.Click(): on TYPE_VIEW_CLICKED of
 * an editable node, if the gap since the previous editable click is < 300ms it is
 * treated as a double-tap and the panel is shown. The panel is a
 * TYPE_APPLICATION_OVERLAY window (uses the "Display over other apps" permission)
 * that does NOT steal focus from the field, so ACTION_PASTE lands in it. The
 * window is laid out within the content area (no FLAG_LAYOUT_IN_SCREEN) and pads
 * itself up by the navigation-bar inset, so the system back/home/recent buttons
 * stay visible and tappable.
 */
public class ClipboardAccessibilityService extends AccessibilityService
        implements ClipAdapter.OnItemListener {

    private static final long DOUBLE_TAP_NS = 300_000_000L; // 300ms, like the original
    private static final int DEFAULT_PANEL_DP = 280;

    private static ClipboardAccessibilityService instance;

    private long lastEditableClickNanos = 0L;
    private CharSequence prevSourcePkg;

    private WindowManager windowManager;
    private View panelView;          // cached, re-used across shows
    private ClipAdapter panelAdapter; // cached, data rebound on show
    private GridLayoutManager panelLayout;
    private boolean panelShowing = false;
    private ClipRepository repo;
    private final Handler main = new Handler(Looper.getMainLooper());

    public static ClipboardAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        repo = ClipRepository.get(this);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        // Enable onKeyEvent so BACK can dismiss the panel.
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return;
        }
        AccessibilityNodeInfo source = event.getSource();
        if (source == null || !source.isEditable()) {
            return;
        }
        CharSequence pkg = source.getPackageName();
        if (prevSourcePkg == null || !prevSourcePkg.equals(pkg)) {
            lastEditableClickNanos = 0L; // reset when the field's app changes
        }
        prevSourcePkg = pkg;

        long now = System.nanoTime();
        if (lastEditableClickNanos != 0L && now - lastEditableClickNanos < DOUBLE_TAP_NS) {
            showClipboardPanel();
        }
        lastEditableClickNanos = now;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (panelShowing && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                dismissPanel();
            }
            return true; // consume both down and up while the panel is open
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        dismissPanel();
        panelView = null;
        panelAdapter = null;
        panelLayout = null;
        instance = null;
        return super.onUnbind(intent);
    }

    // ---- the bottom panel ------------------------------------------------

    /** Inflate + wire the panel once; subsequent shows reuse this view/adapter. */
    @SuppressLint("ClickableViewAccessibility")
    private void ensurePanel() {
        if (panelView != null) {
            return;
        }
        Context themed = new ContextThemeWrapper(this, R.style.Theme_EasyClipboard);
        LayoutInflater inflater = LayoutInflater.from(themed);
        panelView = inflater.inflate(R.layout.overlay_clip_panel, null);

        RecyclerView rv = panelView.findViewById(R.id.panel_recycler);
        rv.setHasFixedSize(true);
        panelLayout = new GridLayoutManager(themed, spanCount());
        rv.setLayoutManager(panelLayout);
        panelAdapter = new ClipAdapter(themed, repo.getClips(), this);
        rv.setAdapter(panelAdapter);

        ImageButton close = panelView.findViewById(R.id.panel_close);
        close.setOnClickListener(v -> dismissPanel());

        // Dismiss on a touch outside the panel.
        panelView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) {
                dismissPanel();
                return true;
            }
            return false;
        });

        // Pad up by the navigation-bar inset so the panel never covers the nav bar.
        panelView.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = navInset(insets);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            return insets;
        });
    }

    @SuppressWarnings("deprecation")
    private int navInset(WindowInsets insets) {
        if (insets == null) {
            return 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        }
        return insets.getSystemWindowInsetBottom();
    }

    private void showClipboardPanel() {
        if (panelShowing) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show();
            return;
        }
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }
        try {
            ensurePanel();

            // Rebind current clips + span BEFORE adding the view (avoids first-frame jank).
            panelLayout.setSpanCount(spanCount());
            panelAdapter.mClips = repo.getClips();
            panelAdapter.notifyDataSetChanged();

            final int height = panelHeightPx();
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            // NOTE: no FLAG_LAYOUT_IN_SCREEN / NO_LIMITS -> window stays above the nav bar.
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    height,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM;

            windowManager.addView(panelView, lp);
            panelShowing = true;

            // Snappy slide-up.
            panelView.setTranslationY(height);
            panelView.animate()
                    .translationY(0f)
                    .setDuration(130)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } catch (Throwable t) {
            t.printStackTrace();
            panelShowing = false;
            safeRemove(panelView);
        }
    }

    private void dismissPanel() {
        if (!panelShowing || panelView == null) {
            return;
        }
        final View v = panelView;
        panelShowing = false;
        try {
            v.animate()
                    .translationY(v.getHeight())
                    .setDuration(120)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> safeRemove(v))
                    .start();
        } catch (Throwable t) {
            safeRemove(v);
        }
    }

    /** Remove from the WindowManager but keep the cached view for reuse. */
    private void safeRemove(View v) {
        if (v == null) {
            return;
        }
        try {
            if (v.getParent() != null && windowManager != null) {
                windowManager.removeView(v);
            }
        } catch (Throwable ignored) {
            // already removed
        }
    }

    private int spanCount() {
        int columns = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt("columncount", 0);
        if (columns > 0) {
            return columns;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int dpWidth = (int) (dm.widthPixels / dm.density);
        return Math.max(2, dpWidth / 160);
    }

    private int panelHeightPx() {
        int dp = DEFAULT_PANEL_DP;
        try {
            dp = PreferenceManager.getDefaultSharedPreferences(this)
                    .getInt("windowsize", DEFAULT_PANEL_DP);
        } catch (Exception ignored) {
            // keep default
        }
        if (dp <= 0) {
            dp = DEFAULT_PANEL_DP;
        }
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // ---- clip tap -> paste back ------------------------------------------

    @Override
    public void onItemClicked(int position) {
        pasteClip(position);
    }

    @Override
    public void onItemDoubleTap(int position) {
        pasteClip(position);
    }

    @Override
    public boolean onItemLongClicked(int position) {
        return false;
    }

    private void pasteClip(int position) {
        List<Clip> clips = repo.getClips();
        if (position < 0 || position >= clips.size()) {
            dismissPanel();
            return;
        }
        final String text = clips.get(position).getText();
        // Save to history (bumps time) and put it on the system clipboard.
        repo.addText(text, this);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Easy Clipboard", text));
        }

        // Paste into the focused editable node after the clipboard settles.
        main.postDelayed(() -> {
            boolean pasted = false;
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                AccessibilityNodeInfo focus = root != null
                        ? root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) : null;
                if (focus != null && focus.isEditable()) {
                    pasted = focus.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                }
            } catch (Throwable ignored) {
                // fall through to the toast
            }
            if (!pasted) {
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
            }
        }, 100);

        dismissPanel();
    }

    /** Kept from the previous scaffold: paste the clipboard into the focused field. */
    public boolean pasteToFocused() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            return false;
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_PASTE);
    }
}
