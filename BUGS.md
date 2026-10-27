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
- **Status:** IN PROGRESS
- **Root Cause:** `screencap -p /sdcard/luffy_screen.png` takes 1,200ms – 1,800ms on the Exynos 9810 due to PNG compression of a 2960x1440 framebuffer on CPU before `am start` is called, creating perceived lag.
- **Fix:** 
  1. Open Luffy UI with minimal initiation delay (~150ms).
  2. Capture screenshot asynchronously using native `screencap` raw dump or surface grab without blocking the activity transition.
- **Prevention Criterion:** Never block the user-facing activity launch on disk I/O or image compression.

---

### BUG-004: Long-Press Firing Ambiguity / Looking Like Single-Click
- **Severity:** Medium (Gesture Distinction)
- **Status:** IN PROGRESS
- **Root Cause:**
  1. Shell timer race condition: if the user releases the button around 400–450ms, the key-up event can arrive before the hold marker is registered, causing a fallback into single-click.
  2. Visual ambiguity: Both Single Click and Long Hold launched the same bottom capsule layout with only minor subtitle differences.
- **Fix:**
  1. Reduce hold threshold to **380ms** and record press start timestamp directly from `getevent` timestamp so release calculation is immune to subshell scheduling jitter.
  2. Clearly differentiate the Long-Hold UI:
     - **Long Hold:** Summons **Full Assistant Voice/Chat Mode** with expanded input focus, voice transcription trigger, and dedicated Assistant Capsule Header.
     - **Single Click:** Summons **Instant Screen Vision Mode** showing active screen thumbnail preview and vision prompt pills ("Summarize screen", "Extract text", "Explain this").
- **Prevention Criterion:** Ensure key duration is computed using hardware timestamps (`UP_TIME - DOWN_TIME >= 380ms`), and provide distinct visual styling between Vision and Assistant modes.

---

### BUG-005: Toast Notifications Leaking Across Screen
- **Severity:** Low (Visual Noise)
- **Status:** RESOLVED
- **Root Cause:** Redundant `Toast.makeText()` calls in `LuffyMainActivity` and `LuffyAccessibilityService` on Daltonizer toggles.
- **Fix:** Removed all `Toast` invocations; state changes are purely visual (monochrome display) or logged to logcat.
- **Prevention Criterion:** Zero user-facing toasts for background toggles.
