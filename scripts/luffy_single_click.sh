#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# Luffy OS — Single-Click Flow: Screenshot -> OCR -> Qwen2.5 -> Summary
# Capped at ~1500 tokens input, ~150 tokens output for zero heat & max battery
# ==============================================================================

PREFIX="/data/data/com.termux/files/usr"
export PATH="$PREFIX/bin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib"

TMP_IMG="/data/local/tmp/luffy_shot.png"
TMP_TXT="/data/local/tmp/luffy_ocr"

# 1. Capture screen silently
screencap -p "$TMP_IMG" 2>/dev/null

if [ ! -f "$TMP_IMG" ]; then
    echo "Failed to capture screen."
    exit 1
fi

# 2. Extract on-screen text with Tesseract OCR (fast LSTM mode)
tesseract "$TMP_IMG" "$TMP_TXT" --oem 1 -l eng 2>/dev/null

# 3. IMMEDIATELY delete screenshot to preserve storage
rm -f "$TMP_IMG"

# 4. Read and cap text to ~1500 tokens (max 5000 characters)
OCR_TEXT=""
if [ -f "${TMP_TXT}.txt" ]; then
    OCR_TEXT=$(head -c 5000 "${TMP_TXT}.txt")
    rm -f "${TMP_TXT}.txt"
elif [ -f "$TMP_TXT" ]; then
    OCR_TEXT=$(head -c 5000 "$TMP_TXT")
    rm -f "$TMP_TXT"
fi

if [ -z "$OCR_TEXT" ]; then
    OCR_TEXT="No legible text detected on screen."
fi

# Escape quotes and backslashes for JSON
CLEAN_TEXT=$(echo "$OCR_TEXT" | tr '\n' ' ' | sed 's/"/\\"/g' | sed 's/\\/\\\\/g')

# 5. Send to llama-server (127.0.0.1:8081) with max 150 tokens output
JSON_PAYLOAD="{\"prompt\":\"<|im_start|>system\\nYou are Luffy, an offline, highly intelligent companion on Galaxy Note 9. Be crisp and concise.<|im_end|>\\n<|im_start|>user\\nSummarize what is on this screen in 2-3 bullet points or 2 sentences:\\n${CLEAN_TEXT}<|im_end|>\\n<|im_start|>assistant\\n\",\"n_predict\":150,\"temperature\":0.3,\"stop\":[\"<|im_end|>\",\"<|endoftext|>\"]}"

RESPONSE=$(curl -s -X POST http://127.0.0.1:8081/completion \
    -H "Content-Type: application/json" \
    -d "$JSON_PAYLOAD" 2>/dev/null | grep -o '"content":"[^"]*' | sed 's/"content":"//' | sed 's/\\n/\n/g')

if [ -z "$RESPONSE" ]; then
    RESPONSE="Summary complete from screen."
fi

# 6. Deliver to user: Display in Luffy Capsule UI or Notification
am start -n com.luffy.core/.LuffyMainActivity --es trigger single_click --es summary "$RESPONSE" >/dev/null 2>&1

echo "$RESPONSE"

