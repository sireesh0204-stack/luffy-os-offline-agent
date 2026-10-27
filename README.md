# Luffy OS: Transforming an 8-Year-Old Flagship into an Offline AI Companion & Battery Tank

[![Device](https://img.shields.io/badge/Hardware-Samsung%20Galaxy%20Note%209-blue.svg)](#)
[![Display](https://img.shields.io/badge/Screen-6.4%22%20Super%20AMOLED%20(True%20Black)-black.svg)](#)
[![AI Engine](https://img.shields.io/badge/Local%20AI-Qwen2.5%201.5B%20(Offline%20GGUF)-red.svg)](#)
[![DAC](https://img.shields.io/badge/Audio-32--bit%20Cirrus%20Logic%20CS47L93-green.svg)](#)
[![E--Ink](https://img.shields.io/badge/Display%20Mode-Monochrome%20Daltonizer-lightgrey.svg)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Luffy OS** is an engineering project that transforms an 8-year-old Samsung Galaxy Note 9 (`SM-N960F`, Exynos 9810, 6GB RAM) into an ultra-battery-friendly, distraction-free **Offline AI Companion, Paper-Texture E-Reader, and Audiophile Device**.

---

## 📑 Core Architectural Pillars

```
+---------------------------------------------------------------------------------+
|                                    LUFFY OS                                     |
+---------------------------------------------------------------------------------+
|  1. BIXBY HARDWARE BUTTON (3-IN-1 PHYSICAL DISPATCHER)                         |
|     • 1-Click: Silent Instant Screenshot -> Luffy Vision Capsule Analysis        |
|     • 2-Click: Instant E-Ink Grayscale Toggle (Color <-> Monochrome)            |
|     • Long Hold: Bixby-Style Animated Capsule UI Sheet                          |
+---------------------------------------------------------------------------------+
|  2. OFFLINE ON-DEVICE AI BRAIN (LUFFY ENGINE)                                   |
|     • 100% Private & Local: Qwen2.5 quantized GGUF via llama.cpp in Termux      |
|     • Zero Internet Required: Never speaks to cloud servers                     |
|     • Direct Tool Calling: Native Calling, SMS, Screen Vision, App Control      |
+---------------------------------------------------------------------------------+
|  3. BATTERY TANK & ZERO-HEATING THERMAL ARCHITECTURE                            |
|     • True AMOLED Pitch Black (#000000) system-wide theme                       |
|     • 40+ Background polling daemons eliminated (ANT+ radios, Knox diagnostics)  |
|     • Quick Settings Tile: 1-Tap Ambient Listening Kill-Switch                  |
|     • Preserved: Iris Scanner & Jio VoLTE cellular calling 100% intact         |
+---------------------------------------------------------------------------------+
|  4. DISTRACTION-FREE PAPER READING (KOREADER + TORBOX WEBDAV)                  |
|     • Sand-grain physical paper texture & 3D page curl animation               |
|     • Torbox Debrid WebDAV direct synchronization into /sdcard/Books             |
+---------------------------------------------------------------------------------+
|  5. 32-BIT CIRRUS LOGIC AUDIOPHILE SOUND (MORNYE FLAC)                          |
|     • Mornye Player driving CS47L93 DAC via AAudio exclusive direct routing     |
+---------------------------------------------------------------------------------+
```

---

## 1. Physical Bixby Button Mapping

Instead of opening Samsung's proprietary cloud assistant, the dedicated physical Bixby button is remapped to 3 distinct hardware gestures:

| Gesture | Action | Implementation | Latency |
| :--- | :--- | :--- | :--- |
| **Single Press** | **Silent Screen Vision** | `LuffyAccessibilityService` captures display without toasts, sliding up Vision Capsule | ~380ms |
| **Double Press** | **Instant E-Ink Toggle** | Hardware debounce dispatches `accessibility_display_daltonizer_enabled` (0 $\leftrightarrow$ 1) | ~200ms |
| **Long Hold** | **Luffy Capsule Sheet** | Window interceptor suppresses Bixby Voice and displays Luffy's bottom capsule | Instant |

---

## 2. Bixby-Style Capsule UI

Luffy's overlay interface replicates the sleek ergonomics of Samsung One UI's signature bottom capsule sheet:
- **True Pitch Black AMOLED (`#000000`):** Turns off OLED pixels completely, saving battery and masking hardware burn-in.
- **Top Curved Corners (`28dp`):** Smooth rounded floating card over the active app.
- **Glowing Neon Pulse Orb (`#2979FF` $\rightarrow$ `#00E5FF`):** Animated center button for voice queries and thought capture.
- **Horizontal Quick Action Chips:**
  - 🌗 `Toggle E-Ink` (Monochrome mode switch)
  - 👁️ `Screen Vision` (Manual instant screenshot analysis)
  - 📖 `KOReader` (Direct jump to sand-grain reader)
  - 🎵 `Mornye Music` (Direct jump to 32-bit DAC player)

---

## 3. Luffy Local AI Brain (100% Offline)

Luffy runs **entirely inside device memory** using a 4-bit quantized **Qwen2.5-1.5B** model via `llama.cpp` inside Termux:
- **Zero Internet Needed:** Functions on airplane mode, in forests, or during network outages.
- **Strict Goal-Driven Directive:**
  > *"You are Luffy, an autonomous, fiercely loyal on-device AI companion. Your primary and absolute mission is to complete whatever the user requests. You may ask clarifying questions only when essential parameters are missing, but your ultimate focus must always be proactive, frictionless completion of the user's objective."*
- **Action Execution:**
  - Phone Calls: Dispatches native `Intent.ACTION_CALL`.
  - SMS Messages: Crafts and triggers `Intent.ACTION_SENDTO`.
  - Screen Vision: Interprets text and UI elements from silent screenshots.

### Setup Script:
```bash
# Inside Termux:
bash luffy_engine/install_luffy_termux.sh
```

---

## 4. Battery Tank Debloat Architecture

To achieve multi-day standby and eliminate thermal throttling on the Exynos 9810:
- **ANT+ Wireless Radios:** Completely disabled (`com.dsi.ant.*`).
- **Telemetry & Push:** Eliminated `diagmonagent`, `securitylogagent`, `sdhms`, `spp.push`.
- **Ecosystem Daemons:** Removed SmartThings, Galaxy Wearable, AllShare, Beacon Manager, and Samsung Health.
- **Zero Redundant Popups:** Disabled Samsung's floating screenshot toolbar (`smart_capture = 0`) and removed all intrusive UI toasts.

Run the automated debloat:
```bash
python scripts/debloat_battery_tank.py
```

---

## 5. E-Reader & Audiophile Setup

### KOReader + Torbox Debrid WebDAV:
- **Paper Aesthetics:** Configured with Sand-grain textured background and 3D page curl animation.
- **Cloud Sync:** Directly mount your Torbox Debrid WebDAV storage into KOReader Cloud Storage to download EPUBs/MOBI files at full fiber speeds directly into `/sdcard/Books`.

### Mornye Audiophile DAC Engine:
- **Hardware:** Galaxy Note 9's 32-bit 384kHz **Cirrus Logic CS47L93** DAC and headphone amplifier through the 3.5mm jack.
- **Playback:** Pure lossless FLAC playback via Android's low-latency AAudio HAL in pitch-black `#000000` AMOLED.

---

## 📁 Repository Structure
```
├── LuffyCore/                 # Native Android application source code
│   ├── src/com/luffy/core/    # Activities, A11y Services, QS Tile
│   ├── res/                   # Drawables, styles, and accessibility configs
│   ├── AndroidManifest.xml    # App manifest with secure settings
│   └── build_apk.py           # Command-line standalone build pipeline
├── luffy_engine/              # Offline Python daemon & prompts
│   ├── luffy_server.py        # Local REST API daemon (Port 8080)
│   ├── luffy_prompts.py       # Goal-driven system prompt & tools
│   └── install_luffy_termux.sh# Automated Termux installation script
├── scripts/                   # System automation utilities
│   └── debloat_battery_tank.py# Surgical battery debloater
└── README.md                  # This master blueprint
```

---

## 📜 License
Distributed under the MIT License. See [LICENSE](LICENSE) for more details.

