# Luffy OS: Comprehensive Regression Test Matrix

Execute this regression checklist whenever any system daemon, key layout, or application update is applied.

---

## 🧪 Regression Checklist Matrix

| ID | Test Case | Target Behavior | Verification Command / Action | Pass / Fail |
|---|---|---|---|:---:|
| **REG-01** | **Iris Biometrics** | Iris scanner unlocks device without crashes | `pm list packages -e \| grep iris` must return `com.samsung.android.server.iris` | `[PASS]` |
| **REG-02** | **Jio VoLTE IMS** | Jio LTE calling and SMS work with HD voice icon | `pm list packages -e \| grep ims` must return `com.sec.ims` | `[PASS]` |
| **REG-03** | **Play Integrity** | Magisk PIF spoofing active, Zygisk loaded | `adb shell su -c "magisk -v"` returns `27.0:MAGISK:R` | `[PASS]` |
| **REG-04** | **Double Click Bixby** | Toggles E-Ink Monochrome $\leftrightarrow$ Color | Tap-tap physical Bixby key within 350ms: Daltonizer toggles, 0 screenshots taken, Luffy does NOT open | `[PASS]` |
| **REG-05** | **Single Click Bixby** | Silent Vision screenshot + instant UI | Single tap Bixby: Screen is captured, Luffy Vision opens with screen pills; NO floating toolbars | `[PENDING]` |
| **REG-06** | **Long Hold Bixby** | Assistant Chat Mode summon | Hold Bixby for >380ms: Full Voice/Chat capsule summons while holding; release takes NO screenshot | `[PENDING]` |
| **REG-07** | **Zero Screen Overlap** | No Samsung toolbars or text toasts | `com.samsung.android.app.smartcapture` disabled; no toast overlays | `[PASS]` |
| **REG-08** | **Battery Tank Sleep** | Zero CPU wakeups in deep sleep | All 42 telemetry / ANT+ services disabled | `[PASS]` |
| **REG-09** | **Offline Local Brain** | Luffy responds without internet connection | Query `http://localhost:8080/api/chat` with airplane mode ON | `[PENDING]` |

---

## 🔄 Regression Execution Protocol
Whenever a bugfix is applied:
1. Run automated command checks for **REG-01**, **REG-02**, **REG-03**, **REG-07**.
2. Perform physical hardware verification for **REG-04**, **REG-05**, **REG-06**.
3. Verify git repositories are clean, synchronized, and pushed.
