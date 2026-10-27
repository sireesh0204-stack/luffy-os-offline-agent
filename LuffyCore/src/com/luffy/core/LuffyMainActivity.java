package com.luffy.core;

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

    private void handleIntent(Intent intent) {
        String trigger = intent != null ? intent.getStringExtra("trigger") : null;

        if ("long_hold".equals(trigger)) {
            // Full Bixby Capsule UI mode
            buildBixbyCapsuleUI("chat");
        } else {
            // Single Press: Screenshot was ALREADY taken silently by Accessibility Service before UI opened!
            buildBixbyCapsuleUI("vision");
        }
    }

    private void toggleEInkMode() {
        try {
            int current = Settings.Secure.getInt(getContentResolver(), "accessibility_display_daltonizer_enabled", 0);
            int next = (current == 1) ? 0 : 1;
            Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer", 0);
            Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer_enabled", next);
            // Silent clean switch - no toasts to avoid overlapping UI
        } catch (Exception e) {
            Toast.makeText(this, "Permission required: WRITE_SECURE_SETTINGS", Toast.LENGTH_SHORT).show();
        }
    }

    private void buildBixbyCapsuleUI(String mode) {
        // Full screen root with dim background
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x88000000); // Translucent dim
        root.setOnClickListener(v -> finish()); // Tap outside to dismiss

        // Bixby Capsule Sheet at bottom
        LinearLayout capsule = new LinearLayout(this);
        capsule.setOrientation(LinearLayout.VERTICAL);
        capsule.setClickable(true); // Don't dismiss when tapping inside capsule

        // Capsule Background: True Pitch Black with rounded top corners
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        float r = dpToPx(28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke((int) dpToPx(1), 0xFF2A2A2A);
        capsule.setBackground(bg);

        int padH = (int) dpToPx(20);
        int padV = (int) dpToPx(16);
        capsule.setPadding(padH, padV, padH, (int) dpToPx(28));

        // Capsule Pull Handle / Drag Bar
        View handle = new View(this);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0xFF555555);
        handleBg.setCornerRadius(dpToPx(3));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams((int) dpToPx(44), (int) dpToPx(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = (int) dpToPx(14);
        capsule.addView(handle, handleParams);

        // Header Title & Status
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
        boolean isVision = "vision".equals(mode);
        tag.setText(isVision ? " · 👁️ Screen Vision" : " · ⚡ Offline Assistant");
        tag.setTextColor(isVision ? 0xFF00E5FF : 0xFF2979FF);
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
        if (isVision) {
            content.setText("Active display captured silently.\nChoose a vision action or ask below:");
        } else {
            content.setText("Autonomous companion active offline.\nWhat would you like me to execute?");
        }
        capsule.addView(content);

        // Action Chips Row (Horizontal Scroll)
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);

        if (isVision) {
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
        } else {
            addChip(chipRow, "📞 Call Contact", v -> {
                content.setText("Luffy: Who would you like to call?");
            });
            addChip(chipRow, "💬 Send Message", v -> {
                content.setText("Luffy: Compose your message below:");
            });
            addChip(chipRow, "☯️ Toggle E-Ink", v -> {
                toggleEInkMode();
                finish();
            });
            addChip(chipRow, "📖 KOReader", v -> {
                launchApp("org.koreader.launcher");
                finish();
            });
            addChip(chipRow, "🎵 Mornye 32-bit DAC", v -> {
                launchApp("com.mornye.app");
                finish();
            });
        }

        chipScroll.addView(chipRow);
        capsule.addView(chipScroll);

        // Input & Orb Section
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(0, (int) dpToPx(14), 0, 0);

        EditText input = new EditText(this);
        input.setHint(isVision ? "Ask a question about this screen..." : "Tell Luffy what to do...");
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

        if (!isVision) {
            input.requestFocus();
        }

        // Bixby Glowing Orb Button
        Button orb = new Button(this);
        orb.setText("⚡");
        orb.setTextColor(0xFFFFFFFF);
        orb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        GradientDrawable orbBg = new GradientDrawable();
        orbBg.setShape(GradientDrawable.OVAL);
        orbBg.setColors(new int[]{0xFF2979FF, 0xFF00E5FF}); // Samsung Bixby Neon Blue Gradient
        orb.setBackground(orbBg);

        int orbSize = (int) dpToPx(42);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(orbSize, orbSize);
        bottomBar.addView(orb, orbParams);

        orb.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (!q.isEmpty()) {
                Toast.makeText(this, "Luffy: Thinking...", Toast.LENGTH_SHORT).show();
                input.setText("");
            }
        });

        capsule.addView(bottomBar);

        // Position capsule at the bottom
        FrameLayout.LayoutParams capsuleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        root.addView(capsule, capsuleParams);

        setContentView(root);
    }

    private void addChip(LinearLayout row, String text, View.OnClickListener listener) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(0xFFEEEEEE);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF181818);
        bg.setCornerRadius(dpToPx(14));
        bg.setStroke((int) dpToPx(1), 0xFF303030);
        chip.setBackground(bg);

        chip.setPadding((int) dpToPx(12), (int) dpToPx(6), (int) dpToPx(12), (int) dpToPx(6));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = (int) dpToPx(8);
        chip.setLayoutParams(p);
        chip.setOnClickListener(listener);
        row.addView(chip);
    }

    private void launchApp(String pkg) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) startActivity(i);
            else Toast.makeText(this, "App not installed: " + pkg, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error launching " + pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
