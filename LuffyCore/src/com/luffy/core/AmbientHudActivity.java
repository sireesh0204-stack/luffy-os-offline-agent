package com.luffy.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;

/**
 * Modern Nothing Phone style fluid animated HUD toast for Ambient Music toggle.
 */
public class AmbientHudActivity extends Activity {

    private static final String AMBIENT_FLAG_PATH = "/data/local/tmp/luffy_ambient";
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private LinearLayout mCapsuleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configure floating HUD window flags
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = (int) dpToPx(48);
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                 | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                 | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        getWindow().setAttributes(lp);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Determine & Toggle State via App SharedPreferences
        SharedPreferences sp = getSharedPreferences("luffy_ambient_prefs", MODE_PRIVATE);
        boolean isCurrentlyOn = sp.getBoolean("ambient_active", false);
        boolean newState;

        if (getIntent().hasExtra("force_state")) {
            newState = getIntent().getBooleanExtra("force_state", !isCurrentlyOn);
        } else {
            newState = !isCurrentlyOn;
        }

        sp.edit().putBoolean("ambient_active", newState).apply();


        // Broadcast to Ambient background service
        Intent toggleBroadcast = new Intent("com.luffy.core.ACTION_AMBIENT_TOGGLE");
        toggleBroadcast.putExtra("enabled", newState);
        sendBroadcast(toggleBroadcast);

        // Subtle haptic tick
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(28);
            }
        } catch (Exception ignored) {}

        // Build Fluid Capsule UI
        mCapsuleView = buildCapsule(newState);
        setContentView(mCapsuleView);

        // Fluid Entrance Animation
        mCapsuleView.setTranslationY(-dpToPx(50));
        mCapsuleView.setAlpha(0f);
        mCapsuleView.setScaleX(0.85f);
        mCapsuleView.setScaleY(0.85f);
        mCapsuleView.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator(2.0f))
                .start();

        // Dismiss after 1500ms with fluid exit
        mHandler.postDelayed(this::dismissWithAnimation, 1500);
    }

    private void dismissWithAnimation() {
        if (mCapsuleView != null) {
            mCapsuleView.animate()
                    .translationY(-dpToPx(50))
                    .alpha(0f)
                    .scaleX(0.88f)
                    .scaleY(0.88f)
                    .setDuration(200)
                    .setInterpolator(new AccelerateInterpolator(1.8f))
                    .withEndAction(this::finish)
                    .start();
        } else {
            finish();
        }
    }

    private LinearLayout buildCapsule(boolean active) {
        LinearLayout capsule = new LinearLayout(this);
        capsule.setOrientation(LinearLayout.HORIZONTAL);
        capsule.setGravity(Gravity.CENTER_VERTICAL);

        int padH = (int) dpToPx(16);
        int padV = (int) dpToPx(10);
        capsule.setPadding(padH, padV, padH, padV);

        // Capsule Background styling
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0A0E14); // Deep AMOLED Black
        bg.setCornerRadius(dpToPx(24));
        int borderColor = active ? 0xFF00E5FF : 0xFF2A2A2A;
        bg.setStroke((int) dpToPx(1.2f), borderColor);
        capsule.setBackground(bg);

        // Left Icon Badge
        TextView icon = new TextView(this);
        icon.setText("♫");
        icon.setTextColor(active ? 0xFF00E5FF : 0xFF777777);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        icon.setGravity(Gravity.CENTER);

        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(active ? 0x2200E5FF : 0x22FFFFFF);
        icon.setBackground(iconBg);

        int iconSize = (int) dpToPx(32);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.rightMargin = (int) dpToPx(12);
        capsule.addView(icon, iconLp);

        // Center Content
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText("AMBIENT LISTENING");
        label.setTextColor(0xFF888888);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setLetterSpacing(0.12f);
        textCol.addView(label);

        TextView status = new TextView(this);
        if (active) {
            status.setText("Music Recognition Active");
            status.setTextColor(0xFF00E5FF);
        } else {
            status.setText("Deactivated (Saved Power)");
            status.setTextColor(0xFFAAAAAA);
        }
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(status);

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.rightMargin = (int) dpToPx(16);
        capsule.addView(textCol, textLp);

        // Right Pulse Indicator
        TextView dot = new TextView(this);
        dot.setText(active ? "●" : "○");
        dot.setTextColor(active ? 0xFF00E676 : 0xFF555555);
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        capsule.addView(dot);

        return capsule;
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }
}
