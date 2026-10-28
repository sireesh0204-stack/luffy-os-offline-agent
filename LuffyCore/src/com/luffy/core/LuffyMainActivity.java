package com.luffy.core;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class LuffyMainActivity extends Activity {
    private static final String TAG = "LuffyMain";
    private static final String LUFFY_API_CHAT = "http://127.0.0.1:8080/api/chat";
    private static final String LUFFY_API_VISION = "http://127.0.0.1:8080/api/vision";

    private ObjectAnimator mPulseAnimator;
    private TextToSpeech mTTS;
    private boolean mTTSReady = false;

    private SpeechRecognizer mSpeechRecognizer;
    private Intent mSpeechIntent;
    private boolean mIsListening = false;

    // View references for dynamic updates
    private TextView mStatusText;
    private TextView mPromptHint;
    private TextView mResponseView;
    private ScrollView mResponseScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initTTS();
        initSpeechRecognizer();
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
        stopListening();
        if (mTTS != null) {
            mTTS.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPulseAnimation();
        stopListening();
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
            mSpeechRecognizer = null;
        }
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
            mTTS = null;
        }
    }

    private void initTTS() {
        try {
            mTTS = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    mTTS.setLanguage(Locale.US);
                    mTTS.setPitch(1.0f);
                    mTTS.setSpeechRate(1.05f);
                    mTTSReady = true;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "TTS init error: " + e.getMessage());
        }
    }

    private void initSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                mSpeechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                mSpeechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

                mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        mIsListening = true;
                        if (mStatusText != null) mStatusText.setText("Listening...");
                        if (mPromptHint != null) mPromptHint.setText("Speak now — Luffy is listening");
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        if (mStatusText != null) mStatusText.setText("Hearing you...");
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {}

                    @Override
                    public void onBufferReceived(byte[] buffer) {}

                    @Override
                    public void onEndOfSpeech() {
                        mIsListening = false;
                        if (mStatusText != null) mStatusText.setText("Thinking...");
                    }

                    @Override
                    public void onError(int error) {
                        mIsListening = false;
                        if (mPromptHint != null) mPromptHint.setText("Tap the orb or a chip to give Luffy a command");
                    }

                    @Override
                    public void onResults(Bundle results) {
                        mIsListening = false;
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String query = matches.get(0).trim();
                            if (!query.isEmpty()) {
                                processVoiceQuery(query);
                            }
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (partial != null && !partial.isEmpty() && mPromptHint != null) {
                            mPromptHint.setText("\"" + partial.get(0) + "...\"");
                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "SpeechRecognizer init error: " + e.getMessage());
        }
    }

    private void startListening() {
        if (mTTS != null && mTTS.isSpeaking()) {
            mTTS.stop();
        }
        if (mSpeechRecognizer != null && mSpeechIntent != null && !mIsListening) {
            try {
                mSpeechRecognizer.startListening(mSpeechIntent);
            } catch (Exception e) {
                Log.e(TAG, "Start listening error: " + e.getMessage());
            }
        }
    }

    private void stopListening() {
        if (mSpeechRecognizer != null && mIsListening) {
            try {
                mSpeechRecognizer.stopListening();
            } catch (Exception ignored) {}
            mIsListening = false;
        }
    }

    private void processVoiceQuery(String query) {
        if (mStatusText != null) mStatusText.setText("Thinking...");
        if (mPromptHint != null) mPromptHint.setText("\"" + query + "\"");

        JSONObject payload = new JSONObject();
        try {
            payload.put("message", query);
        } catch (Exception ignored) {}

        sendHttpRequest(LUFFY_API_CHAT, payload.toString(), reply -> {
            if (mStatusText != null) mStatusText.setText("Luffy:");
            if (mResponseView != null) mResponseView.setText(reply);
            if (mResponseScroll != null) mResponseScroll.setVisibility(View.VISIBLE);
            speak(reply);
        });
    }

    private void speak(String text) {
        if (mTTSReady && mTTS != null && text != null) {
            String clean = text.replaceAll("\\[ACTION:[^\\]]+\\]", "")
                               .replace("*", "")
                               .replace("#", "")
                               .trim();
            if (!clean.isEmpty()) {
                mTTS.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "luffy_voice");
            }
        }
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
        stopListening();
        String action = intent != null ? intent.getStringExtra("action") : null;
        if ("set_black_wallpaper".equals(action)) {
            applyPitchBlackWallpaper();
            finish();
            return;
        }

        String trigger = intent != null ? intent.getStringExtra("trigger") : null;

        if ("long_hold".equals(trigger)) {
            // Long Hold: Dedicated True Voice Assistant UI
            buildVoiceAssistantUI();
        } else {
            // Single Click: Screen Vision Capsule UI
            buildVisionCapsuleUI();
        }
    }

    private void applyPitchBlackWallpaper() {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            Bitmap bitmap = Bitmap.createBitmap(1440, 2960, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(0xFF000000);
            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
            Toast.makeText(this, "AMOLED Pitch Black Wallpaper Applied", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Pitch black wallpaper successfully applied to System & Lockscreen");
        } catch (Exception e) {
            Log.e(TAG, "Wallpaper application error: " + e.getMessage());
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
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> finish());

        LinearLayout capsule = new LinearLayout(this);
        capsule.setOrientation(LinearLayout.VERTICAL);
        capsule.setClickable(true);
        capsule.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
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

        // Start breathing animation
        coreOrb.post(() -> {
            try {
                stopPulseAnimation();
                mPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        coreOrb,
                        PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.14f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.14f)
                );
                mPulseAnimator.setDuration(900);
                mPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
                mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                mPulseAnimator.start();
            } catch (Exception ignored) {}
        });

        capsule.addView(orbContainer);

        // Voice Status Labels
        mStatusText = new TextView(this);
        mStatusText.setText("Listening...");
        mStatusText.setTextColor(0xFFFFFFFF);
        mStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        mStatusText.setTypeface(null, android.graphics.Typeface.BOLD);
        mStatusText.setGravity(Gravity.CENTER);
        capsule.addView(mStatusText);

        mPromptHint = new TextView(this);
        mPromptHint.setText("Speak now — or tap a chip below");
        mPromptHint.setTextColor(0xFF888888);
        mPromptHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mPromptHint.setGravity(Gravity.CENTER);
        mPromptHint.setPadding(0, (int) dpToPx(6), 0, (int) dpToPx(16));
        capsule.addView(mPromptHint);

        // Scrollable Response Area
        mResponseScroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dpToPx(80));
        scrollParams.bottomMargin = (int) dpToPx(12);
        mResponseScroll.setLayoutParams(scrollParams);
        mResponseScroll.setVisibility(View.GONE);

        mResponseView = new TextView(this);
        mResponseView.setTextColor(0xFFEEEEEE);
        mResponseView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mResponseView.setLineSpacing(dpToPx(3), 1.0f);
        mResponseScroll.addView(mResponseView);
        capsule.addView(mResponseScroll);

        // Quick Voice Action Chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER);

        addChip(chipRow, "💬 Hello Luffy", v -> {
            processVoiceQuery("Hello Luffy! What is your mission on this device?");
        });

        addChip(chipRow, "📞 Call Contact", v -> {
            processVoiceQuery("Call my favorite contact right now");
        });

        addChip(chipRow, "☯️ Toggle E-Ink", v -> {
            toggleEInkMode();
            mStatusText.setText("E-Ink Grayscale toggled");
            speak("E-Ink toggled");
        });

        addChip(chipRow, "🎵 Mornye 32-bit DAC", v -> {
            launchApp("com.zarz.mornye");
            finish();
        });

        addChip(chipRow, "📖 Open KOReader", v -> {
            launchApp("org.koreader.launcher");
            finish();
        });

        chipScroll.addView(chipRow);
        capsule.addView(chipScroll);

        // Tap Orb to start listening or toggle voice input
        coreOrb.setOnClickListener(v -> {
            if (mTTS != null && mTTS.isSpeaking()) {
                mTTS.stop();
            } else {
                startListening();
            }
        });

        FrameLayout.LayoutParams capsuleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capsuleParams.gravity = Gravity.BOTTOM;
        root.addView(capsule, capsuleParams);

        setContentView(root);

        // Auto-start listening on summon
        new Handler(Looper.getMainLooper()).postDelayed(this::startListening, 300);
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

        // Content Area (Scrollable response)
        ScrollView contentScroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dpToPx(100));
        scrollParams.topMargin = (int) dpToPx(10);
        scrollParams.bottomMargin = (int) dpToPx(10);
        contentScroll.setLayoutParams(scrollParams);

        TextView content = new TextView(this);
        content.setTextColor(0xFFCCCCCC);
        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        content.setText("Active display captured silently.\nChoose a vision action or ask below:");
        content.setLineSpacing(dpToPx(2), 1.0f);
        contentScroll.addView(content);
        capsule.addView(contentScroll);

        // Action Chips Row
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);

        addChip(chipRow, "✨ Summarize Screen", v -> {
            content.setText("Luffy: Reading screen and summarizing...");
            sendHttpRequest(LUFFY_API_VISION, "{\"query\": \"Summarize the main content on this screen concisely.\"}", reply -> {
                content.setText(reply);
                speak(reply);
            });
        });

        addChip(chipRow, "📝 Extract Text", v -> {
            content.setText("Luffy: Extracting screen text elements...");
            sendHttpRequest(LUFFY_API_VISION, "{\"query\": \"Extract all key text, numbers, and titles visible on this screen.\"}", reply -> {
                content.setText(reply);
            });
        });

        addChip(chipRow, "💡 Explain", v -> {
            content.setText("Luffy: Explaining active screen...");
            sendHttpRequest(LUFFY_API_VISION, "{\"query\": \"Explain what is happening on this screen in simple terms.\"}", reply -> {
                content.setText(reply);
                speak(reply);
            });
        });

        addChip(chipRow, "🌐 Translate", v -> {
            content.setText("Luffy: Translating visible text...");
            sendHttpRequest(LUFFY_API_VISION, "{\"query\": \"Translate any non-English text on this screen to clear English.\"}", reply -> {
                content.setText(reply);
            });
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
                sendHttpRequest(LUFFY_API_VISION, "{\"query\": \"" + q.replace("\"", "\\\"") + "\"}", reply -> {
                    content.setText(reply);
                    speak(reply);
                });
            }
        });

        capsule.addView(bottomBar);

        FrameLayout.LayoutParams capsuleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capsuleParams.gravity = Gravity.BOTTOM;
        root.addView(capsule, capsuleParams);

        setContentView(root);
    }

    private void sendHttpRequest(String urlStr, String jsonBody, ResponseCallback callback) {
        new Thread(() -> {
            String resultText;
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(30000);

                byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    JSONObject respJson = new JSONObject(sb.toString());
                    resultText = respJson.optString("reply", "No response received.");
                } else {
                    resultText = "Luffy server returned status: " + code;
                }
            } catch (Exception e) {
                resultText = "Luffy offline: " + e.getMessage();
            }

            final String finalResult = resultText;
            runOnUiThread(() -> callback.onResult(finalResult));
        }).start();
    }

    interface ResponseCallback {
        void onResult(String reply);
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
