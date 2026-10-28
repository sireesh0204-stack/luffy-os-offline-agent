package com.luffy.core;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Lightweight Accessibility Service for Luffy OS window safety & content inspection.
 * Hardware key interception is 100% managed by kernel daemon luffy_keyd.sh.
 */
public class LuffyAccessibilityService extends AccessibilityService {
    private static final String TAG = "LuffyA11y";
    private static LuffyAccessibilityService sInstance;

    public static LuffyAccessibilityService getInstance() {
        return sInstance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.d(TAG, "LuffyAccessibilityService connected (zero key interception mode).");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sInstance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Fallback safety: if Samsung Bixby window ever attempts to display, dismiss it immediately
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkgName = event.getPackageName();
            if (pkgName != null && pkgName.toString().contains("bixby")) {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        }
    }

    @Override
    public void onInterrupt() {}
}
