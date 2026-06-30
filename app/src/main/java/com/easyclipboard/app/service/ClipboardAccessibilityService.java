package com.easyclipboard.app.service;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Accessibility paste helper. Kept minimal: it can find the currently focused
 * editable node and perform ACTION_PASTE on it, which lets the app paste into
 * other apps without an Xposed hook.
 */
public class ClipboardAccessibilityService extends AccessibilityService {

    private static ClipboardAccessibilityService instance;

    public static ClipboardAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op: this service is used on demand via pasteToFocused().
    }

    @Override
    public void onInterrupt() {
        // No-op.
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    /** Paste the system clipboard into the focused editable field, if any. */
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
