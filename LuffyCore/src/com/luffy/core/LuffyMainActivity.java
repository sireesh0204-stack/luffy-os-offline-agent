package com.luffy.core;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.speech.tts.Voice;
import android.text.InputType;
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

import org.json.JSONArray;
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
                    try {
                        if (mTTS.getVoices() != null) {
                            Voice bestMaleVoice = null;
                            for (Voice v : mTTS.getVoices()) {
                                if (v.getLocale() != null && "en".equalsIgnoreCase(v.getLocale().getLanguage())) {
                                    String name = v.getName().toLowerCase();
                                    Log.d(TAG, "Available voice: " + name);
                                    // Google TTS real male models: rjs, gbb, gbd, iom, iob, tpd
                                    // Strictly reject female models: fis (Fiona), gbc (Claire), gbh (Hannah), iof, ioh, sfg
                                    boolean isMale = (name.contains("rjs") || name.contains("gbb") || name.contains("gbd") || 
                                                      name.contains("iom") || name.contains("iob") || name.contains("tpd")) &&
                                                     !name.contains("fis") && !name.contains("gbc") && !name.contains("gbh") &&
                                                     !name.contains("iof") && !name.contains("ioh");
                                    if (isMale) {
                                        bestMaleVoice = v;
                                        if (!v.isNetworkConnectionRequired()) {
                                            break;
                                        }
                                    }
                                }
                            }
                            if (bestMaleVoice != null) {
                                mTTS.setVoice(bestMaleVoice);
                                Log.i(TAG, "Luffy switched to male voice: " + bestMaleVoice.getName());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Male voice selection error: " + e.getMessage());
                    }
                    mTTS.setPitch(0.82f); // Deep masculine pitch
                    mTTS.setSpeechRate(0.98f);
                    mTTSReady = true;
                }
            }, "com.google.android.tts");
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
                mTTS.setPitch(0.82f);
                mTTS.setSpeechRate(0.98f);
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
        } else if ("single_click".equals(trigger) || "vision".equals(trigger)) {
            // Double Click: Screen Vision Capsule UI
            buildVisionCapsuleUI();
        } else {
            // Launched from App Drawer / Olauncher!
            buildLuffyAppMainUI();
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

    // ==========================================
    // 3. FULL LUFFY OS MAIN APPLICATION UI
    // ==========================================
    private FrameLayout mTabContentContainer;
    private Button mTabBtnAbout;
    private Button mTabBtnVault;
    private Button mTabBtnApps;
    private Button mTabBtnContacts;

    private void buildLuffyAppMainUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int padH = (int) dpToPx(16);
        root.setPadding(padH, (int) dpToPx(36), padH, (int) dpToPx(12));

        // Top App Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.setPadding(0, 0, 0, (int) dpToPx(10));

        TextView appTitle = new TextView(this);
        appTitle.setText("LUFFY OS");
        appTitle.setTextColor(0xFF00E5FF);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appTitle.setLetterSpacing(0.08f);
        header.addView(appTitle);

        TextView subTitle = new TextView(this);
        subTitle.setText("Offline AI Companion • Identity Profile • Encrypted Vault");
        subTitle.setTextColor(0xFF888888);
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subTitle.setPadding(0, (int) dpToPx(2), 0, (int) dpToPx(8));
        header.addView(subTitle);

        // Status Badges
        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        addBadge(badgeRow, "● Qwen 1.5B Local", 0xFF00E676);
        addBadge(badgeRow, "● AES-256 Vault", 0xFF00E5FF);
        addBadge(badgeRow, "● Zero Cloud", 0xFFFFD600);
        header.addView(badgeRow);

        root.addView(header);

        // Navigation Tabs Bar
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabRow.setPadding(0, (int) dpToPx(6), 0, (int) dpToPx(12));

        mTabBtnAbout = createNavTab("ABOUT");
        mTabBtnVault = createNavTab("VAULT");
        mTabBtnContacts = createNavTab("CONTACTS");
        mTabBtnApps = createNavTab("APPS");

        tabRow.addView(mTabBtnAbout);
        tabRow.addView(mTabBtnVault);
        tabRow.addView(mTabBtnContacts);
        tabRow.addView(mTabBtnApps);
        root.addView(tabRow);

        // Dynamic Tab Content Container
        mTabContentContainer = new FrameLayout(this);
        mTabContentContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView(mTabContentContainer);

        mTabBtnAbout.setOnClickListener(v -> selectAppTab(0));
        mTabBtnVault.setOnClickListener(v -> selectAppTab(1));
        mTabBtnContacts.setOnClickListener(v -> selectAppTab(2));
        mTabBtnApps.setOnClickListener(v -> selectAppTab(3));

        selectAppTab(0);
        setContentView(root);
    }

    private Button createNavTab(String title) {
        Button btn = new Button(this);
        btn.setText(title);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) dpToPx(38), 1.0f);
        lp.rightMargin = (int) dpToPx(4);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void styleTabButton(Button btn, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        if (active) {
            bg.setColor(0xFF00E5FF);
            bg.setCornerRadius(dpToPx(8));
            btn.setTextColor(0xFF000000);
        } else {
            bg.setColor(0xFF141414);
            bg.setCornerRadius(dpToPx(8));
            bg.setStroke((int) dpToPx(1), 0xFF282828);
            btn.setTextColor(0xFF888888);
        }
        btn.setBackground(bg);
    }

    private void selectAppTab(int index) {
        styleTabButton(mTabBtnAbout, index == 0);
        styleTabButton(mTabBtnVault, index == 1);
        styleTabButton(mTabBtnContacts, index == 2);
        styleTabButton(mTabBtnApps, index == 3);

        mTabContentContainer.removeAllViews();
        if (index == 0) {
            mTabContentContainer.addView(buildAboutMeView());
        } else if (index == 1) {
            mTabContentContainer.addView(buildVaultView());
        } else if (index == 2) {
            mTabContentContainer.addView(buildContactsView());
        } else {
            mTabContentContainer.addView(buildAppAccessView());
        }
    }

    private void addBadge(LinearLayout parent, String text, int color) {
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextColor(color);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setPadding((int) dpToPx(6), (int) dpToPx(3), (int) dpToPx(6), (int) dpToPx(3));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF121212);
        bg.setCornerRadius(dpToPx(4));
        bg.setStroke((int) dpToPx(1), color & 0x44FFFFFF);
        badge.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = (int) dpToPx(6);
        badge.setLayoutParams(lp);
        parent.addView(badge);
    }

    // ------------------------------------------
    // TAB 1: ABOUT ME (Identity & Signup Profile)
    // ------------------------------------------
    private View buildAboutMeView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(this);
        desc.setText("Luffy uses your identity dataset exclusively to autofill universal signup forms when you command him. Never shared with any cloud.");
        desc.setTextColor(0xFFAAAAAA);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setPadding(0, 0, 0, (int) dpToPx(14));
        layout.addView(desc);

        SharedPreferences sp = getSharedPreferences("luffy_profile", MODE_PRIVATE);

        EditText etName = createFormField(layout, "Full Name", sp.getString("name", "Sireesh"));
        EditText etEmail = createFormField(layout, "Email (Gmail)", sp.getString("email", "sireeshmeets@gmail.com"));
        EditText etBday = createFormField(layout, "Birthday (DOB)", sp.getString("birthday", "02-04-2000"));
        EditText etPhone = createFormField(layout, "Phone Number", sp.getString("phone", "+91"));
        EditText etUser = createFormField(layout, "Preferred Username", sp.getString("username", "sireesh"));

        // Fetch fresh profile from server in background
        sendRawHttp("http://127.0.0.1:8080/api/profile", "GET", null, resp -> {
            try {
                JSONObject json = new JSONObject(resp);
                if (json.has("profile")) {
                    JSONObject p = json.getJSONObject("profile");
                    runOnUiThread(() -> {
                        if (p.has("name")) etName.setText(p.optString("name"));
                        if (p.has("email")) etEmail.setText(p.optString("email"));
                        if (p.has("birthday")) etBday.setText(p.optString("birthday"));
                        if (p.has("phone")) etPhone.setText(p.optString("phone"));
                        if (p.has("username")) etUser.setText(p.optString("username"));
                    });
                }
            } catch (Exception ignored) {}
        });

        // Save Profile Button
        Button btnSave = new Button(this);
        btnSave.setText("SAVE PROFILE");
        btnSave.setTextColor(0xFF000000);
        btnSave.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnSave.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF00E5FF);
        btnBg.setCornerRadius(dpToPx(10));
        btnSave.setBackground(btnBg);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dpToPx(44));
        btnLp.topMargin = (int) dpToPx(12);
        btnLp.bottomMargin = (int) dpToPx(16);
        btnSave.setLayoutParams(btnLp);

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String bday = etBday.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String user = etUser.getText().toString().trim();

            sp.edit()
                .putString("name", name)
                .putString("email", email)
                .putString("birthday", bday)
                .putString("phone", phone)
                .putString("username", user)
                .apply();

            JSONObject payload = new JSONObject();
            try {
                payload.put("name", name);
                payload.put("email", email);
                payload.put("birthday", bday);
                payload.put("phone", phone);
                payload.put("username", user);
            } catch (Exception ignored) {}

            sendRawHttp("http://127.0.0.1:8080/api/profile", "POST", payload.toString(), res -> {
                runOnUiThread(() -> Toast.makeText(this, "✓ Profile Saved for Luffy Autofill", Toast.LENGTH_SHORT).show());
            });
        });
        layout.addView(btnSave);

        // Security Assurance Card
        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(0xFF0D1B2A);
        infoBg.setCornerRadius(dpToPx(10));
        infoBg.setStroke((int) dpToPx(1), 0xFF1B3A5A);
        infoCard.setBackground(infoBg);
        int padCard = (int) dpToPx(12);
        infoCard.setPadding(padCard, padCard, padCard, padCard);

        TextView infoTitle = new TextView(this);
        infoTitle.setText("🛡️ Autofill Trigger Constraint");
        infoTitle.setTextColor(0xFF00E5FF);
        infoTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        infoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        infoCard.addView(infoTitle);

        TextView infoBody = new TextView(this);
        infoBody.setText("Luffy will ONLY execute signups or autofill forms when you explicitly wake him up by holding the Bixby button and command him ('Sign up with my info' or 'Fill this form'). Never unprompted.");
        infoBody.setTextColor(0xFFCCCCCC);
        infoBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        infoBody.setPadding(0, (int) dpToPx(4), 0, 0);
        infoCard.addView(infoBody);

        layout.addView(infoCard);
        scroll.addView(layout);
        return scroll;
    }

    private EditText createFormField(LinearLayout parent, String label, String defaultValue) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFF00E5FF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, (int) dpToPx(8), 0, (int) dpToPx(4));
        parent.addView(tv);

        EditText et = new EditText(this);
        et.setText(defaultValue);
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFF121212);
        etBg.setCornerRadius(dpToPx(8));
        etBg.setStroke((int) dpToPx(1), 0xFF2A2A2A);
        et.setBackground(etBg);
        int pad = (int) dpToPx(10);
        et.setPadding(pad, pad, pad, pad);
        parent.addView(et);
        return et;
    }

    // ------------------------------------------
    // TAB 2: VAULT & PASSWORD MANAGER TOOL
    // ------------------------------------------
    private View buildVaultView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Add New Credential Card
        LinearLayout addCard = new LinearLayout(this);
        addCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF0F151B);
        cardBg.setCornerRadius(dpToPx(10));
        cardBg.setStroke((int) dpToPx(1), 0xFF1E2D3A);
        addCard.setBackground(cardBg);
        int p = (int) dpToPx(12);
        addCard.setPadding(p, p, p, p);

        TextView addTitle = new TextView(this);
        addTitle.setText("+ Store New Credential");
        addTitle.setTextColor(0xFF00E5FF);
        addTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        addTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        addCard.addView(addTitle);

        EditText etDomain = createFormField(addCard, "Website / App Domain", "github.com");
        EditText etUser = createFormField(addCard, "Username / Email", "sireeshmeets@gmail.com");
        EditText etPass = createFormField(addCard, "Password", "");

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, (int) dpToPx(10), 0, 0);

        Button btnGen = new Button(this);
        btnGen.setText("GENERATE STRONG");
        btnGen.setTextColor(0xFF00E5FF);
        btnGen.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        GradientDrawable genBg = new GradientDrawable();
        genBg.setColor(0xFF141F28);
        genBg.setCornerRadius(dpToPx(8));
        genBg.setStroke((int) dpToPx(1), 0xFF00E5FF);
        btnGen.setBackground(genBg);
        LinearLayout.LayoutParams lpGen = new LinearLayout.LayoutParams(0, (int) dpToPx(40), 1.0f);
        lpGen.rightMargin = (int) dpToPx(6);
        btnGen.setLayoutParams(lpGen);

        btnGen.setOnClickListener(v -> {
            sendRawHttp("http://127.0.0.1:8080/api/vault/generate", "GET", null, resp -> {
                try {
                    JSONObject j = new JSONObject(resp);
                    String genPwd = j.optString("password", "");
                    runOnUiThread(() -> etPass.setText(genPwd));
                } catch (Exception ignored) {}
            });
        });
        btnRow.addView(btnGen);

        Button btnSaveVault = new Button(this);
        btnSaveVault.setText("SAVE TO VAULT");
        btnSaveVault.setTextColor(0xFF000000);
        btnSaveVault.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btnSaveVault.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(0xFF00E5FF);
        saveBg.setCornerRadius(dpToPx(8));
        btnSaveVault.setBackground(saveBg);
        btnSaveVault.setLayoutParams(new LinearLayout.LayoutParams(0, (int) dpToPx(40), 1.0f));

        btnRow.addView(btnSaveVault);
        addCard.addView(btnRow);
        layout.addView(addCard);

        // Saved Accounts List Header
        TextView listHeader = new TextView(this);
        listHeader.setText("SAVED VAULT ACCOUNTS (AES-256)");
        listHeader.setTextColor(0xFFFFFFFF);
        listHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        listHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        listHeader.setPadding(0, (int) dpToPx(18), 0, (int) dpToPx(8));
        layout.addView(listHeader);

        EditText etVaultSearch = new EditText(this);
        etVaultSearch.setHint("🔍 Search 490+ passwords...");
        etVaultSearch.setHintTextColor(0xFF777777);
        etVaultSearch.setTextColor(0xFFFFFFFF);
        etVaultSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(0xFF141414);
        searchBg.setCornerRadius(dpToPx(8));
        searchBg.setStroke((int) dpToPx(1), 0xFF2A2A2A);
        etVaultSearch.setBackground(searchBg);
        int pad = (int) dpToPx(10);
        etVaultSearch.setPadding(pad, pad, pad, pad);
        layout.addView(etVaultSearch);

        LinearLayout accountsContainer = new LinearLayout(this);
        accountsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams accLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        accLp.topMargin = (int) dpToPx(10);
        accountsContainer.setLayoutParams(accLp);
        layout.addView(accountsContainer);

        ArrayList<JSONObject> cachedAccounts = new ArrayList<>();

        Runnable renderFiltered = () -> {
            accountsContainer.removeAllViews();
            String query = etVaultSearch.getText().toString().trim().toLowerCase();
            int count = 0;
            for (JSONObject acc : cachedAccounts) {
                String domain = acc.optString("domain");
                String user = acc.optString("username");
                if (!query.isEmpty() && !domain.toLowerCase().contains(query) && !user.toLowerCase().contains(query)) {
                    continue;
                }
                count++;
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable iBg = new GradientDrawable();
                iBg.setColor(0xFF121212);
                iBg.setCornerRadius(dpToPx(8));
                iBg.setStroke((int) dpToPx(1), 0xFF222222);
                item.setBackground(iBg);
                int pItem = (int) dpToPx(10);
                item.setPadding(pItem, pItem, pItem, pItem);
                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                itemLp.bottomMargin = (int) dpToPx(8);
                item.setLayoutParams(itemLp);

                LinearLayout topRow = new LinearLayout(this);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);
                topRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout textCol = new LinearLayout(this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView dView = new TextView(this);
                dView.setText(domain);
                dView.setTextColor(0xFF00E5FF);
                dView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                dView.setTypeface(null, android.graphics.Typeface.BOLD);
                textCol.addView(dView);

                TextView uView = new TextView(this);
                uView.setText(user);
                uView.setTextColor(0xFFCCCCCC);
                uView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                textCol.addView(uView);

                TextView pwdDisplay = new TextView(this);
                pwdDisplay.setText("Password: ••••••••••••");
                pwdDisplay.setTextColor(0xFF888888);
                pwdDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                pwdDisplay.setPadding(0, (int) dpToPx(3), 0, 0);
                textCol.addView(pwdDisplay);

                topRow.addView(textCol);

                // SHOW / REVEAL Button
                Button btnShow = new Button(this);
                btnShow.setText("SHOW");
                btnShow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                btnShow.setTextColor(0xFFFFCC00);
                GradientDrawable sBg = new GradientDrawable();
                sBg.setColor(0xFF262010);
                sBg.setCornerRadius(dpToPx(6));
                sBg.setStroke((int) dpToPx(1), 0xFFFFCC00);
                btnShow.setBackground(sBg);
                LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams((int) dpToPx(56), (int) dpToPx(34));
                sLp.rightMargin = (int) dpToPx(6);
                btnShow.setLayoutParams(sLp);

                btnShow.setOnClickListener(vShow -> {
                    if ("HIDE".equals(btnShow.getText().toString())) {
                        pwdDisplay.setText("Password: ••••••••••••");
                        pwdDisplay.setTextColor(0xFF888888);
                        btnShow.setText("SHOW");
                        return;
                    }
                    JSONObject pReq = new JSONObject();
                    try { pReq.put("domain", domain); } catch (Exception ignored) {}
                    sendRawHttp("http://127.0.0.1:8080/api/vault/get", "POST", pReq.toString(), getResp -> {
                        try {
                            JSONObject gObj = new JSONObject(getResp);
                            JSONObject aObj = gObj.optJSONObject("account");
                            if (aObj != null) {
                                String pwd = aObj.optString("password");
                                runOnUiThread(() -> {
                                    pwdDisplay.setText("Key: " + pwd);
                                    pwdDisplay.setTextColor(0xFF00FF88);
                                    pwdDisplay.setTypeface(null, android.graphics.Typeface.BOLD);
                                    btnShow.setText("HIDE");
                                });
                            }
                        } catch (Exception ignored) {}
                    });
                });
                topRow.addView(btnShow);

                // Copy Password Button
                Button btnCopy = new Button(this);
                btnCopy.setText("COPY");
                btnCopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                btnCopy.setTextColor(0xFF00E5FF);
                GradientDrawable cBg = new GradientDrawable();
                cBg.setColor(0xFF1E1E1E);
                cBg.setCornerRadius(dpToPx(6));
                btnCopy.setBackground(cBg);
                btnCopy.setLayoutParams(new LinearLayout.LayoutParams((int) dpToPx(56), (int) dpToPx(34)));

                btnCopy.setOnClickListener(vCopy -> {
                    JSONObject pReq = new JSONObject();
                    try { pReq.put("domain", domain); } catch (Exception ignored) {}
                    sendRawHttp("http://127.0.0.1:8080/api/vault/get", "POST", pReq.toString(), getResp -> {
                        try {
                            JSONObject gObj = new JSONObject(getResp);
                            JSONObject aObj = gObj.optJSONObject("account");
                            if (aObj != null) {
                                String pwd = aObj.optString("password");
                                runOnUiThread(() -> {
                                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                    cm.setPrimaryClip(ClipData.newPlainText("password", pwd));
                                    Toast.makeText(this, "Copied password for " + domain, Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (Exception ignored) {}
                    });
                });
                topRow.addView(btnCopy);
                item.addView(topRow);
                accountsContainer.addView(item);
            }
            if (count == 0) {
                TextView empty = new TextView(this);
                empty.setText("No matching accounts found.");
                empty.setTextColor(0xFF777777);
                empty.setPadding(0, (int) dpToPx(10), 0, 0);
                accountsContainer.addView(empty);
            }
        };

        etVaultSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderFiltered.run();
            }
            public void afterTextChanged(android.text.Editable s) {}
        });

        Runnable refreshAccounts = () -> {
            sendRawHttp("http://127.0.0.1:8080/api/vault/list", "GET", null, resp -> {
                runOnUiThread(() -> {
                    cachedAccounts.clear();
                    try {
                        JSONObject j = new JSONObject(resp);
                        JSONArray arr = j.optJSONArray("accounts");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                cachedAccounts.add(arr.getJSONObject(i));
                            }
                        }
                    } catch (Exception ignored) {}
                    renderFiltered.run();
                });
            });
        };

        btnSaveVault.setOnClickListener(v -> {
            String domain = etDomain.getText().toString().trim();
            String user = etUser.getText().toString().trim();
            String pwd = etPass.getText().toString().trim();
            if (domain.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Domain and password cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject payload = new JSONObject();
            try {
                payload.put("domain", domain);
                payload.put("username", user);
                payload.put("password", pwd);
            } catch (Exception ignored) {}
            sendRawHttp("http://127.0.0.1:8080/api/vault/add", "POST", payload.toString(), res -> {
                runOnUiThread(() -> {
                    Toast.makeText(this, "✓ Stored in Encrypted Vault", Toast.LENGTH_SHORT).show();
                    etPass.setText("");
                    refreshAccounts.run();
                });
            });
        });

        refreshAccounts.run();
        scroll.addView(layout);
        return scroll;
    }

    // ------------------------------------------
    // ------------------------------------------
    // TAB 3: CONTACTS BROWSER
    // ------------------------------------------
    private View buildContactsView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        EditText etSearch = new EditText(this);
        etSearch.setHint("🔍 Search contacts...");
        etSearch.setHintTextColor(0xFF777777);
        etSearch.setTextColor(0xFFFFFFFF);
        etSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(0xFF141414);
        searchBg.setCornerRadius(dpToPx(8));
        searchBg.setStroke((int) dpToPx(1), 0xFF2A2A2A);
        etSearch.setBackground(searchBg);
        int pad = (int) dpToPx(10);
        etSearch.setPadding(pad, pad, pad, pad);
        root.addView(etSearch);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollLp.topMargin = (int) dpToPx(10);
        scroll.setLayoutParams(scrollLp);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        root.addView(scroll);

        new Thread(() -> {
            ArrayList<String[]> contactList = new ArrayList<>();
            try {
                android.net.Uri uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                String[] projection = new String[]{
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                };
                android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, 
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(0);
                        String number = cursor.getString(1);
                        if (name != null && number != null) {
                            contactList.add(new String[]{name, number});
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Contacts query error: " + e.getMessage());
            }

            runOnUiThread(() -> {
                Runnable renderContacts = () -> {
                    listContainer.removeAllViews();
                    String query = etSearch.getText().toString().trim().toLowerCase();
                    int count = 0;
                    for (String[] itemData : contactList) {
                        String name = itemData[0];
                        String num = itemData[1];
                        if (!query.isEmpty() && !name.toLowerCase().contains(query) && !num.contains(query)) {
                            continue;
                        }
                        count++;
                        LinearLayout item = new LinearLayout(this);
                        item.setOrientation(LinearLayout.HORIZONTAL);
                        item.setGravity(Gravity.CENTER_VERTICAL);
                        GradientDrawable iBg = new GradientDrawable();
                        iBg.setColor(0xFF10141A);
                        iBg.setCornerRadius(dpToPx(8));
                        iBg.setStroke((int) dpToPx(1), 0xFF1C2836);
                        item.setBackground(iBg);
                        int pItem = (int) dpToPx(10);
                        item.setPadding(pItem, pItem, pItem, pItem);
                        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        itemLp.bottomMargin = (int) dpToPx(8);
                        item.setLayoutParams(itemLp);

                        LinearLayout textCol = new LinearLayout(this);
                        textCol.setOrientation(LinearLayout.VERTICAL);
                        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                        TextView nameView = new TextView(this);
                        nameView.setText(name);
                        nameView.setTextColor(0xFF00E5FF);
                        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
                        textCol.addView(nameView);

                        TextView numView = new TextView(this);
                        numView.setText(num);
                        numView.setTextColor(0xFFCCCCCC);
                        numView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                        textCol.addView(numView);
                        item.addView(textCol);

                        Button btnCall = new Button(this);
                        btnCall.setText("CALL");
                        btnCall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                        btnCall.setTextColor(0xFF00FF88);
                        GradientDrawable callBg = new GradientDrawable();
                        callBg.setColor(0xFF14241C);
                        callBg.setCornerRadius(dpToPx(6));
                        callBg.setStroke((int) dpToPx(1), 0xFF00FF88);
                        btnCall.setBackground(callBg);
                        btnCall.setLayoutParams(new LinearLayout.LayoutParams((int) dpToPx(60), (int) dpToPx(34)));
                        btnCall.setOnClickListener(vCall -> {
                            Intent callIntent = new Intent(Intent.ACTION_CALL);
                            callIntent.setData(android.net.Uri.parse("tel:" + num.replaceAll("[^\\\\d+]", "")));
                            startActivity(callIntent);
                        });
                        item.addView(btnCall);
                        listContainer.addView(item);
                    }
                    if (count == 0) {
                        TextView empty = new TextView(this);
                        empty.setText("No contacts found.");
                        empty.setTextColor(0xFF777777);
                        empty.setPadding(0, (int) dpToPx(20), 0, 0);
                        listContainer.addView(empty);
                    }
                };
                renderContacts.run();
                etSearch.addTextChangedListener(new android.text.TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        renderContacts.run();
                    }
                    public void afterTextChanged(android.text.Editable s) {}
                });
            });
        }).start();

        return root;
    }


    // TAB 4: APP ACCESS & SCREEN INSPECTION
    // ------------------------------------------
    private View buildAppAccessView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Screen Inspector Card
        LinearLayout inspectCard = new LinearLayout(this);
        inspectCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF0F151B);
        cardBg.setCornerRadius(dpToPx(10));
        cardBg.setStroke((int) dpToPx(1), 0xFF1E2D3A);
        inspectCard.setBackground(cardBg);
        int p = (int) dpToPx(12);
        inspectCard.setPadding(p, p, p, p);

        TextView inspectTitle = new TextView(this);
        inspectTitle.setText("👁️ Live Screen Perception");
        inspectTitle.setTextColor(0xFF00E5FF);
        inspectTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        inspectTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        inspectCard.addView(inspectTitle);

        TextView inspectDesc = new TextView(this);
        inspectDesc.setText("See exactly what Luffy's vision and OCR engine perceives on your phone screen:");
        inspectDesc.setTextColor(0xFFAAAAAA);
        inspectDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        inspectDesc.setPadding(0, (int) dpToPx(4), 0, (int) dpToPx(8));
        inspectCard.addView(inspectDesc);

        TextView inspectResult = new TextView(this);
        inspectResult.setText("[Tap inspect below to run screen perception]");
        inspectResult.setTextColor(0xFFCCCCCC);
        inspectResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        inspectResult.setPadding((int) dpToPx(8), (int) dpToPx(8), (int) dpToPx(8), (int) dpToPx(8));
        GradientDrawable resBg = new GradientDrawable();
        resBg.setColor(0xFF141414);
        resBg.setCornerRadius(dpToPx(6));
        inspectResult.setBackground(resBg);
        inspectCard.addView(inspectResult);

        Button btnInspect = new Button(this);
        btnInspect.setText("INSPECT CURRENT SCREEN NOW");
        btnInspect.setTextColor(0xFF000000);
        btnInspect.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnInspect.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF00E5FF);
        btnBg.setCornerRadius(dpToPx(8));
        btnInspect.setBackground(btnBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dpToPx(40));
        btnLp.topMargin = (int) dpToPx(10);
        btnInspect.setLayoutParams(btnLp);

        btnInspect.setOnClickListener(v -> {
            inspectResult.setText("Reading screen with Tesseract OCR...");
            sendRawHttp("http://127.0.0.1:8080/api/screen/inspect", "GET", null, resp -> {
                runOnUiThread(() -> {
                    try {
                        JSONObject j = new JSONObject(resp);
                        String txt = j.optString("text", "No text detected.");
                        inspectResult.setText(txt);
                    } catch (Exception e) {
                        inspectResult.setText("Inspection error: " + e.getMessage());
                    }
                });
            });
        });
        inspectCard.addView(btnInspect);
        layout.addView(inspectCard);

        // Installed Apps Card
        TextView appsHeader = new TextView(this);
        appsHeader.setText("INSTALLED USER APPLICATIONS & ACCESS");
        appsHeader.setTextColor(0xFFFFFFFF);
        appsHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        appsHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        appsHeader.setPadding(0, (int) dpToPx(18), 0, (int) dpToPx(8));
        layout.addView(appsHeader);

        LinearLayout appsContainer = new LinearLayout(this);
        appsContainer.setOrientation(LinearLayout.VERTICAL);
        layout.addView(appsContainer);

        sendRawHttp("http://127.0.0.1:8080/api/apps", "GET", null, resp -> {
            runOnUiThread(() -> {
                try {
                    JSONObject j = new JSONObject(resp);
                    JSONArray arr = j.optJSONArray("apps");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            String pkg = arr.getString(i);
                            LinearLayout row = new LinearLayout(this);
                            row.setOrientation(LinearLayout.VERTICAL);
                            GradientDrawable rBg = new GradientDrawable();
                            rBg.setColor(0xFF121212);
                            rBg.setCornerRadius(dpToPx(8));
                            rBg.setStroke((int) dpToPx(1), 0xFF222222);
                            row.setBackground(rBg);
                            int padRow = (int) dpToPx(8);
                            row.setPadding(padRow, padRow, padRow, padRow);
                            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            rowLp.bottomMargin = (int) dpToPx(6);
                            row.setLayoutParams(rowLp);

                            TextView pName = new TextView(this);
                            pName.setText(pkg);
                            pName.setTextColor(0xFFFFFFFF);
                            pName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            pName.setTypeface(null, android.graphics.Typeface.BOLD);
                            row.addView(pName);

                            TextView pAccess = new TextView(this);
                            pAccess.setText("✓ Screen Vision OCR  •  ✓ Autofill on Bixby Command");
                            pAccess.setTextColor(0xFF00E676);
                            pAccess.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                            row.addView(pAccess);

                            appsContainer.addView(row);
                        }
                    }
                } catch (Exception ignored) {}
            });
        });

        scroll.addView(layout);
        return scroll;
    }

    private void sendRawHttp(String urlStr, String method, String jsonBody, RawCallback callback) {
        new Thread(() -> {
            String resultText;
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(15000);
                if ("POST".equalsIgnoreCase(method) && jsonBody != null) {
                    conn.setDoOutput(true);
                    byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(out);
                    }
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
                    resultText = sb.toString();
                } else {
                    resultText = "{\"status\":\"error\", \"code\":" + code + "}";
                }
            } catch (Exception e) {
                resultText = "{\"status\":\"offline\", \"error\":\"" + e.getMessage() + "\"}";
            }
            final String finalResult = resultText;
            runOnUiThread(() -> callback.onResult(finalResult));
        }).start();
    }

    interface RawCallback {
        void onResult(String response);
    }

}
