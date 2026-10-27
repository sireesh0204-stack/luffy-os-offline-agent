package com.luffy.core;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class LuffyAccessibilityService extends AccessibilityService {
    private static final String TAG = "LuffyA11y";
    private static LuffyAccessibilityService sInstance;

    public static LuffyAccessibilityService getInstance() {
        return sInstance;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int clickCount = 0;
    private boolean isLongPressTriggered = false;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            isLongPressTriggered = true;
            clickCount = 0;
            handler.removeCallbacks(singleClickRunnable);
            Log.d(TAG, "Hardware Key: Long Hold Triggered -> Launching Luffy Capsule UI");
            Intent intent = new Intent(LuffyAccessibilityService.this, LuffyMainActivity.class);
            intent.putExtra("trigger", "long_hold");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    };

    private final Runnable singleClickRunnable = new Runnable() {
        @Override
        public void run() {
            clickCount = 0;
            Log.d(TAG, "Hardware Key: Single Click Confirmed -> Taking Silent Screenshot FIRST");
            
            // 1. Take silent screenshot BEFORE opening Luffy UI
            takeScreenshot();

            // 2. Open Luffy Vision Capsule after screenshot captures clean screen
            handler.postDelayed(() -> {
                Intent intent = new Intent(LuffyAccessibilityService.this, LuffyMainActivity.class);
                intent.putExtra("trigger", "single_click");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }, 300);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.d(TAG, "LuffyAccessibilityService connected with Key Event Filter active.");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sInstance = null;
        return super.onUnbind(intent);
    }

    public void takeScreenshot() {
        try {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        } catch (Exception e) {
            Log.e(TAG, "Error taking screenshot: " + e.getMessage());
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();

        // 142 is KEYCODE_F12 (mapped in Generic.kl), 1082 is Samsung Bixby keycode, 703 is raw scancode
        if (keyCode == KeyEvent.KEYCODE_F12 || keyCode == 1082 || scanCode == 703) {
            int action = event.getAction();

            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    isLongPressTriggered = false;
                    // Start long-press timer (450ms)
                    handler.postDelayed(longPressRunnable, 450);
                }
                return true; // Consume event completely from Samsung OS
            } else if (action == KeyEvent.ACTION_UP) {
                // Cancel pending long press on release
                handler.removeCallbacks(longPressRunnable);

                if (isLongPressTriggered) {
                    // Was a long hold, swallow release
                    isLongPressTriggered = false;
                    return true;
                }

                clickCount++;
                if (clickCount == 1) {
                    // Wait 300ms to see if a second click arrives
                    handler.postDelayed(singleClickRunnable, 300);
                } else if (clickCount >= 2) {
                    // Double click confirmed! Cancel single click screenshot immediately
                    handler.removeCallbacks(singleClickRunnable);
                    clickCount = 0;
                    Log.d(TAG, "Hardware Key: Double Click Confirmed -> Toggle E-Ink");
                    handleDoubleClick();
                }
                return true; // Consume event completely from Samsung OS
            }
        }
        return super.onKeyEvent(event);
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

    public void handleDoubleClick() {
        try {
            int current = Settings.Secure.getInt(getContentResolver(), "accessibility_display_daltonizer_enabled", 0);
            int next = (current == 1) ? 0 : 1;
            Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer", 0);
            Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer_enabled", next);
            Log.d(TAG, "Daltonizer switched to: " + next);
        } catch (Exception e) {
            Log.e(TAG, "Daltonizer error: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {}
}
