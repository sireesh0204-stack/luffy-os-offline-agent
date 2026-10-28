# Luffy OS: Bug Tracking & Prevention Ledger

This document tracks all identified bugs, edge cases, root cause analyses, fix implementations, and strict prevention criteria to prevent regressions.

---

## 📋 Active & Resolved Bugs

### BUG-001: Samsung Screenshot Floating Toolbar Overlapping Luffy UI
- **Severity:** High (UI Obfuscation)
- **Status:** RESOLVED
- **Root Cause:** Samsung One UI's `com.samsung.android.app.smartcapture` package intercepts `GLOBAL_ACTION_TAKE_SCREENSHOT` and draws an interactive floating overlay over top of the foreground window.
- **Fix:** 
  1. Disabled package via root: `pm disable com.samsung.android.app.smartcapture`.
  2. Configured system properties: `settings put system smart_capture 0`.
- **Prevention Criterion:** Never rely solely on `smart_capture 0` setting; verify `com.samsung.android.app.smartcapture` package state is `disabled`.

---

### BUG-002: Double-Click Triggering Phantom Screenshots
- **Severity:** High (Unintended Side-Effects)
- **Status:** RESOLVED
- **Root Cause:** Asynchronous subshells spawned by the first click were not tracking their process IDs, allowing the 350ms single-click timer to expire and fire even after a second click arrived.
- **Fix:** Implemented active PID tracking and instant process termination (`kill $TIMER_PID 2>/dev/null`) upon receiving the second keypress.
- **Prevention Criterion:** Any multi-tap gesture detection must explicitly cancel pending single-tap tasks before executing the multi-tap action.

---

### BUG-003: Single-Click High Latency (Slow Opening of Luffy)
- **Severity:** Medium (User Experience / Latency)
- **Status:** RESOLVED
- **Root Cause:** `screencap -p` was taking 2.67s due to full PNG CPU compression.
- **Fix:** Switched to raw framebuffer dump (`screencap /sdcard/luffy_screen.raw`) benchmarking at 0.16s (16x faster) and reduced debounce window to 260ms. Verified fast and snappy.
- **Prevention Criterion:** Never block the user-facing activity launch on image compression.

---

### BUG-004: Long-Press Firing Ambiguity / Looking Like Single-Click
- **Severity:** Medium (Gesture Distinction)
- **Status:** RESOLVED
- **Root Cause:** UI for Assistant and Vision shared similar bottom sheet structures.
- **Fix:** Redesigned Assistant mode into a dedicated Voice Assistant interface with pulsing audio visualizer, separate listening state, and dedicated voice chips.

---

### BUG-006: Long-Hold Firing on Release Instead of During Hold
- **Severity:** High (Core UX / Interaction Pattern)
- **Status:** RESOLVED
- **Root Cause:** Duration was evaluated inside `KEY UP` (`UP_TIME - DOWN_TIME`), requiring the user to release the key before the assistant would trigger.
- **Fix:** Start an asynchronous hold timer on `KEY DOWN`. If held for >= 380ms, summon the Voice Assistant immediately *while the button is still being held*. On `KEY UP`, cancel the hold timer and swallow release if already triggered.
- **Prevention Criterion:** Hardware voice assistants must trigger while held, never after release.

---

### BUG-007: Blank Full-Screen Grey View & Animator CPU Leak
- **Severity:** Critical (UI Lockout / Battery Drain)
- **Status:** RESOLVED
- **Root Cause:**
  1. In programmatic UI building, `capsule.addView(handle)` was called without explicit `LayoutParams`. Under Android's `ViewGroup`, this defaulted to `MATCH_PARENT x MATCH_PARENT`. Because `handle` had background `#444444`, it rendered as a solid blank grey screen over the entire display.
  2. An infinite `ObjectAnimator` loop on the voice orb ran continuously on the Choreographer thread without lifecycle cancellation, consuming up to 23% CPU and preventing the UI thread from entering an idle state.
- **Fix:**
  1. Passed explicit `handle.getLayoutParams()` (`44dp x 5dp`, center horizontal) to `capsule.addView(handle, handle.getLayoutParams())`.
  2. Implemented `mPulseAnimator` lifecycle management in `onPause()` and `onDestroy()` to cancel animation when the activity is dismissed, reducing CPU usage to 0.0%.
- **Prevention Criterion:** Always pass explicit `LayoutParams` when adding custom programmatic views to `ViewGroup`. Always hook infinite animators to `onPause()` / `onDestroy()`.

---

### BUG-005: Toast Notifications Leaking Across Screen
- **Severity:** Low (Visual Noise)
- **Status:** RESOLVED
- **Root Cause:** Redundant `Toast.makeText()` calls in `LuffyMainActivity` and `LuffyAccessibilityService` on Daltonizer toggles.
- **Fix:** Removed all `Toast` invocations; state changes are purely visual (monochrome display) or logged to logcat.
- **Prevention Criterion:** Zero user-facing toasts for background toggles.

---

### BUG-008: Samsung Note 9 Low-Brightness OLED Green Tint & CABC Black Crush
- **Severity:** High (Display Quality & Visual Comfort)
- **Status:** RESOLVED
- **Root Cause:** At low brightness (<25%), Samsung Note 9 hardware Content-Adaptive Backlight Control (CABC) and low-frequency PWM gamma switch cause an elevated green/grey black floor where pitch-black `#000000` pixels fail to turn off.
- **Fix:** Disabled CABC hardware driver via `echo 0 > /sys/class/lcd/panel/adaptive_control` in Magisk service `/data/adb/service.d/luffy_ai_service.sh` and calibrated minimum brightness floor to 35% (value 90).
- **Prevention Criterion:** Keep `adaptive_control` disabled in boot scripts on all Exynos 9810 OLED devices.

---

### BUG-009: Offline Password Vault & Safety Model Refusal Handling
- **Severity:** Medium (Tool Availability & Model Alignment)
- **Status:** RESOLVED
- **Root Cause:** Base Qwen2.5-1.5B safety alignment refuses generic prompts containing "What is my password" unless the system prompt explicitly frames credentials retrieval as authorized local encrypted password vault management.
- **Fix:** Designed `luffy_vault.py` with local AES-256-CBC + PBKDF2 encryption, integrated `[ACTION:VAULT_GET]`, `[ACTION:SIGNUP_FILL]`, and `[ACTION:LOGIN_FILL]` into Luffy's system prompt, and added auto-healing `ensure_llama_running()` daemon management.
- **Prevention Criterion:** Always provide structured tool tags and explicit authorized assistant persona for security/credential retrieval.

---

### BUG-010: Transparent Navigation Bar Touch Intercept Conflict with Bottom Buttons
- **Severity:** High (Touch Interception & Usability)
- **Status:** RESOLVED
- **Root Cause:** Setting `navigationbar_color` to `0` (transparent) caused One UI window manager to render application windows behind navigation bar insets. While visually transparent, the navigation bar window consumed or intercepted touches near the bottom edge, making bottom buttons (e.g., form submit, setup dialog buttons) difficult or impossible to tap. Additionally, Samsung gesture overlays collided with 2-button navigation layout.
- **Fix:** Reverted `navigationbar_color` and `navigationbar_current_color` to solid pitch black (`-16777216` / `0xFF000000`), disabled conflicting gesture package `com.samsung.internal.systemui.navbar.sec_gestural_no_hint`, set `need_dark_navigationbar` to `1` and `navigationbar_use_theme_default` to `0`.
- **Prevention Criterion:** Always maintain solid navigation bar insets when using button navigation bars to ensure clean touch dispatch to foreground views.

---

### BUG-011: Conversational Preamble on Screen Vision Questions
- **Severity:** Medium (Response Directness & Speed)
- **Status:** RESOLVED
- **Root Cause:** When presented with quiz or test questions extracted via OCR, Qwen provided conversational greetings or preambles ("Based on the text on screen, the answer is...") rather than immediate direct answers.
- **Fix:** Added automatic regex question detection in `/api/vision` (`?`, `what is`, `which of`, `how to`, `choose the`, `options A/B/C/D`) and appended strict prompt directive: *"YOUR VERY FIRST RESPONSE SENTENCE MUST BE THE DIRECT AND DEFINITIVE ANSWER TO THAT QUESTION (e.g. 'Answer: B) ...'), followed by 1 concise explanation sentence."*
- **Prevention Criterion:** Interrogative vision prompts must enforce direct-answer first-sentence formatting.



