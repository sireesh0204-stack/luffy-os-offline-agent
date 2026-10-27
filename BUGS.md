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

