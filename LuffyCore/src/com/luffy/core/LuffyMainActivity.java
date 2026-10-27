package com.luffy.core;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class LuffyMainActivity extends Activity {
    private static final String TAG = "LuffyMain";
    private ObjectAnimator mPulseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPulseAnimation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPulseAnimation();
    }

    private void stopPulseAnimation() {
        if (mPulseAnimator != null) {
            try {
                mPulseAnimator.cancel();
            } catch (Exception ignored) {}
            mPulseAnimator = null;
        }
    }

    private void handleIntent(Intent intent) {
        stopPulseAnimation();
        String trigger = intent != null ? intent.getStringExtra("trigger") : null;

        if ("long_hold".equals(trigger)) {
            // Long Hold: Dedicated True Voice Assistant UI
            buildVoiceAssistantUI();
        } else {
            // Single Click: Screen Vision Capsule UI
            buildVisionCapsuleUI();
        }
    }

    private void toggleEInkMode() {
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

    // ==========================================
    // 1. DEDICATED VOICE ASSISTANT UI (Long Hold)
    // ==========================================
    private void buildVoiceAssistantUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000); // Dark dimmed backdrop
        root.setOnClickListener(v -> finish()); // Tap outside to dismiss

        LinearLayout capsule = new LinearLayout(this);
        capsule.setOrientation(LinearLayout.VERTICAL);
        capsule.setClickable(true);
        capsule.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000); // True pitch black AMOLED
        float r = dpToPx(30);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke((int) dpToPx(1), 0xFF1E2A38);
        capsule.setBackground(bg);

        int padH = (int) dpToPx(24);
        capsule.setPadding(padH, (int) dpToPx(16), padH, (int) dpToPx(32));

        // Pull Handle
        View handle = createHandle();
        capsule.addView(handle, handle.getLayoutParams());

        // Header Title
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("LUFFY VOICE ASSISTANT");
        title.setTextColor(0xFF00E5FF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        header.addView(title);

        capsule.addView(header);

        // Center Stage: Pulsing Animated Voice Orb
        FrameLayout orbContainer = new FrameLayout(this);
        LinearLayout.LayoutParams orbContainerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dpToPx(140));
        orbContainerParams.topMargin = (int) dpToPx(12);
        orbContainerParams.bottomMargin = (int) dpToPx(8);
        orbContainer.setLayoutParams(orbContainerParams);

        // Outer Glow Ring
        View glowRing = new View(this);
        int ringSize = (int) dpToPx(100);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(ringSize, ringSize);
        ringParams.gravity = Gravity.CENTER;
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        ringBg.setColor(0x2200E5FF);
        glowRing.setBackground(ringBg);
        orbContainer.addView(glowRing, ringParams);

        // Core Glowing Orb
        View coreOrb = new View(this);
        int coreSize = (int) dpToPx(72);
        FrameLayout.LayoutParams coreParams = new FrameLayout.LayoutParams(coreSize, coreSize);
        coreParams.gravity = Gravity.CENTER;
        GradientDrawable coreBg = new GradientDrawable();
        coreBg.setShape(GradientDrawable.OVAL);
        coreBg.setColors(new int[]{0xFF00E5FF, 0xFF2979FF});
        coreBg.setOrientation(GradientDrawable.Orientation.TL_BR);
        coreOrb.setBackground(coreBg);
        orbContainer.addView(coreOrb, coreParams);

        // Soft breathing animation safely posted after view attachment
        coreOrb.post(() -> {
            try {
                stopPulseAnimation();
                mPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        coreOrb,
                        PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.12f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.12f)
                );
                mPulseAnimator.setDuration(1000);
                mPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
                mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                mPulseAnimator.start();
            } catch (Exception ignored) {}
        });

        capsule.addView(orbContainer);

        // Voice Status Labels
        TextView status = new TextView(this);
        status.setText("Listening...");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        capsule.addView(status);

        TextView promptHint = new TextView(this);
        promptHint.setText("Say: 'Call...', 'Message...', 'Play music', or 'Read'");
        promptHint.setTextColor(0xFF888888);
        promptHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        promptHint.setGravity(Gravity.CENTER);
        promptHint.setPadding(0, (int) dpToPx(6), 0, (int) dpToPx(20));
        capsule.addView(promptHint);

        // Quick Voice Action Chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER);

        addChip(chipRow, "📞 Call Contact", v -> {
            status.setText("Calling contact...");
        });
        addChip(chipRow, "💬 Send Message", v -> {
            status.setText("Listening for message...");
        });
        addChip(chipRow, "🎵 Mornye 32-bit DAC", v -> {
            launchApp("com.mornye.app");
            finish();
        });
        addChip(chipRow, "📖 Open KOReader", v -> {
            launchApp("org.koreader.launcher");
            finish();
        });
        addChip(chipRow, "☯️ Toggle E-Ink", v -> {
            toggleEInkMode();
            finish();
        });

        chipScroll.addView(chipRow);
        capsule.addView(chipScroll);

        FrameLayout.LayoutParams capsuleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capsuleParams.gravity = Gravity.BOTTOM;
        root.addView(capsule, capsuleParams);

        setContentView(root);
    }

    // ==========================================
    // 2. DEDICATED SCREEN VISION UI (Single Click)
    // ==========================================
    private void buildVisionCapsuleUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x88000000);
        root.setOnClickListener(v -> finish());

        LinearLayout capsule = new LinearLayout(this);
        capsule.setOrientation(LinearLayout.VERTICAL);
        capsule.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        float r = dpToPx(28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke((int) dpToPx(1), 0xFF2A2A2A);
        capsule.setBackground(bg);

        int padH = (int) dpToPx(20);
        int padV = (int) dpToPx(16);
        capsule.setPadding(padH, padV, padH, (int) dpToPx(28));

        // Pull Handle
        View handle = createHandle();
        capsule.addView(handle, handle.getLayoutParams());

        // Header Title
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("LUFFY");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);

        TextView tag = new TextView(this);
        tag.setText(" · 👁️ Screen Vision");
        tag.setTextColor(0xFF00E5FF);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tag.setTypeface(null, android.graphics.Typeface.BOLD);
        tag.setPadding((int) dpToPx(8), 0, 0, 0);
        header.addView(tag);
        capsule.addView(header);

        // Content Area
        TextView content = new TextView(this);
        content.setTextColor(0xFFCCCCCC);
        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        content.setPadding(0, (int) dpToPx(10), 0, (int) dpToPx(12));
        content.setText("Active display captured silently.\nChoose a vision action or ask below:");
        capsule.addView(content);

        // Action Chips Row
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);

        addChip(chipRow, "✨ Summarize Screen", v -> {
            content.setText("Luffy: Summarizing on-screen content...");
        });
        addChip(chipRow, "📝 Extract Text", v -> {
            content.setText("Luffy: Extracting visible text and numbers...");
        });
        addChip(chipRow, "💡 Explain Content", v -> {
            content.setText("Luffy: Explaining active screen...");
        });
        addChip(chipRow, "🌐 Translate", v -> {
            content.setText("Luffy: Translating screen text...");
        });

        chipScroll.addView(chipRow);
        capsule.addView(chipScroll);

        // Input & Action Section
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(0, (int) dpToPx(14), 0, 0);

        EditText input = new EditText(this);
        input.setHint("Ask a question about this screen...");
        input.setHintTextColor(0xFF555555);
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0xFF141414);
        inputBg.setCornerRadius(dpToPx(20));
        inputBg.setStroke((int) dpToPx(1), 0xFF2A2A2A);
        input.setBackground(inputBg);
        input.setPadding((int) dpToPx(16), (int) dpToPx(10), (int) dpToPx(16), (int) dpToPx(10));

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        inputParams.rightMargin = (int) dpToPx(12);
        bottomBar.addView(input, inputParams);

        Button orb = new Button(this);
        orb.setText("⚡");
        orb.setTextColor(0xFFFFFFFF);
        orb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        GradientDrawable orbBg = new GradientDrawable();
        orbBg.setShape(GradientDrawable.OVAL);
        orbBg.setColors(new int[]{0xFF2979FF, 0xFF00E5FF});
        orb.setBackground(orbBg);

        int orbSize = (int) dpToPx(42);
        bottomBar.addView(orb, new LinearLayout.LayoutParams(orbSize, orbSize));

        orb.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (!q.isEmpty()) {
                content.setText("Luffy: Analyzing '" + q + "'...");
                input.setText("");
            }
        });

        capsule.addView(bottomBar);

        FrameLayout.LayoutParams capsuleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capsuleParams.gravity = Gravity.BOTTOM;
        root.addView(capsule, capsuleParams);

        setContentView(root);
    }

    private View createHandle() {
        View handle = new View(this);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0xFF444444);
        handleBg.setCornerRadius(dpToPx(3));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams((int) dpToPx(44), (int) dpToPx(5));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = (int) dpToPx(14);
        handle.setLayoutParams(handleParams);
        return handle;
    }

    private void addChip(LinearLayout parent, String text, View.OnClickListener listener) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(0xFFE0E0E0);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(0xFF161616);
        chipBg.setCornerRadius(dpToPx(16));
        chipBg.setStroke((int) dpToPx(1), 0xFF333333);
        chip.setBackground(chipBg);

        int padH = (int) dpToPx(14);
        int padV = (int) dpToPx(8);
        chip.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = (int) dpToPx(8);
        chip.setLayoutParams(params);

        chip.setOnClickListener(listener);
        parent.addView(chip);
    }

    private void launchApp(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        } else {
            Toast.makeText(this, packageName + " is not installed yet", Toast.LENGTH_SHORT).show();
        }
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
