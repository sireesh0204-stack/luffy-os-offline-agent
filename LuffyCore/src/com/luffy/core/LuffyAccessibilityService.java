package com.luffy.core;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class LuffyAccessibilityService extends AccessibilityService {
    private static final String TAG = "LuffyA11y";
    private static LuffyAccessibilityService sInstance;

    public static LuffyAccessibilityService getInstance() {
        return sInstance;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBixbyInterceptTime = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.d(TAG, "LuffyAccessibilityService connected.");
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
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkgName = event.getPackageName();
            if (pkgName != null && pkgName.toString().equals("com.samsung.android.bixby.agent")) {
                long now = System.currentTimeMillis();
                if (now - lastBixbyInterceptTime < 600) {
                    // Debounce rapid window transitions
                    return;
                }
                lastBixbyInterceptTime = now;

                CharSequence clsName = event.getClassName();
                String cls = clsName != null ? clsName.toString() : "";
                Log.d(TAG, "Bixby Window Intercepted: " + cls);

                // Case 1: Voice / Conversation Window -> Long Hold Action
                if (cls.contains("Conversation") || cls.contains("Voice") || cls.contains("Popup")) {
                    Log.d(TAG, "Detected Long Hold (Bixby Voice). Rerouting to Luffy Capsule...");
                    performGlobalAction(GLOBAL_ACTION_BACK);

                    handler.postDelayed(() -> {
                        Intent intent = new Intent(this, LuffyMainActivity.class);
                        intent.putExtra("trigger", "long_hold");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }, 50);
                } 
                // Case 2: Any other Bixby Screen (AssistantHome / Main) -> Double Click Action
                else {
                    Log.d(TAG, "Detected Double Click (Bixby Home). Intercepting & toggling E-Ink...");
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    handleDoubleClick();
                }
            }
        }
    }

    public void handleDoubleClick() {
        try {
            int current = Settings.Secure.getInt(getContentResolver(), "accessibility_display_daltonizer_enabled", 0);
            int next = (current == 1) ? 0 : 1;
            Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer", 0);
            Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer_enabled", next);
            String mode = (next == 1) ? "E-Ink Grayscale (ON)" : "True Color (ON)";
            Toast.makeText(this, "Luffy: " + mode, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Daltonizer switched to: " + next);
        } catch (Exception e) {
            Toast.makeText(this, "Permission required: WRITE_SECURE_SETTINGS", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Daltonizer error: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {}
}
