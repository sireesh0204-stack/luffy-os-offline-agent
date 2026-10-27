#!/usr/bin/env python3
"""
Luffy OS Core Companion Server
Runs natively on Samsung Galaxy Note 9 on 127.0.0.1:8080.
Connects to local llama-server on 127.0.0.1:8081 for offline neural inference.
Provides device tool execution (calling, messaging, E-Ink toggle, app launch, screen vision).
"""

import http.server
import json
import os
import subprocess
import sys
import threading
import time
import urllib.request
import urllib.error

HOST = "127.0.0.1"
PORT = 8080
LLAMA_URL = "http://127.0.0.1:8081/completion"

SYSTEM_PROMPT = """You are Luffy, an offline, highly intelligent, fiercely loyal AI companion running natively on Samsung Galaxy Note 9 (Luffy OS).
Your prime directive: Complete what the user asks for. You may ask concise clarifying questions if essential, but your main goal is always goal-completion.
Keep responses concise, natural, direct, and conversational. No corporate fluff.
If the user wants a phone action, output a tool tag at the end of your response:
- [ACTION:CALL number]
- [ACTION:SMS number | message]
- [ACTION:EINK toggle]
- [ACTION:LAUNCH package_name]
- [ACTION:TIMER seconds]
"""

def execute_device_action(action_str):
    try:
        parts = action_str.split(":", 1)
        if len(parts) < 2:
            return None
        cmd_type = parts[0].strip().upper()
        args = parts[1].strip()

        if cmd_type == "CALL":
            num = args.replace(" ", "")
            subprocess.run(["am", "start", "-a", "android.intent.action.CALL", "-d", f"tel:{num}"], capture_output=True)
            return f"Dialing {num}..."
        elif cmd_type == "EINK":
            # Toggle Daltonizer
            res = subprocess.run(["settings", "get", "secure", "accessibility_display_daltonizer_enabled"], capture_output=True, text=True)
            curr = res.stdout.strip()
            nxt = "0" if curr == "1" else "1"
            subprocess.run(["settings", "put", "secure", "accessibility_display_daltonizer", "0"])
            subprocess.run(["settings", "put", "secure", "accessibility_display_daltonizer_enabled", nxt])
            return "E-Ink Grayscale toggled."
        elif cmd_type == "LAUNCH":
            subprocess.run(["monkey", "-p", args, "-c", "android.intent.category.LAUNCHER", "1"], capture_output=True)
            return f"Opened {args}."
        elif cmd_type == "TIMER":
            secs = int(args)
            subprocess.run(["am", "start", "-a", "android.intent.action.SET_TIMER", "--ei", "android.intent.extra.alarm.LENGTH", str(secs), "--ez", "android.intent.extra.alarm.SKIP_UI", "true"], capture_output=True)
            return f"Timer set for {secs} seconds."
    except Exception as e:
        return f"Action error: {e}"
    return None

def query_llama(prompt, max_tokens=256, temperature=0.7):
    full_prompt = f"<|im_start|>system\n{SYSTEM_PROMPT}<|im_end|>\n<|im_start|>user\n{prompt}<|im_end|>\n<|im_start|>assistant\n"
    payload = json.dumps({
        "prompt": full_prompt,
        "n_predict": max_tokens,
        "temperature": temperature,
        "stop": ["<|im_end|>", "<|endoftext|>"]
    }).encode("utf-8")

    req = urllib.request.Request(
        LLAMA_URL,
        data=payload,
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            content = data.get("content", "").strip()
            
            # Check for actions
            action_result = None
            if "[ACTION:" in content:
                start = content.find("[ACTION:") + 8
                end = content.find("]", start)
                if end != -1:
                    action_cmd = content[start:end].strip()
                    action_result = execute_device_action(action_cmd)
                    # Clean the action tag from speech output
                    clean_content = content[:content.find("[ACTION:")].strip()
                    if action_result:
                        content = f"{clean_content}\n\n✓ {action_result}" if clean_content else action_result
            return content
    except Exception as e:
        return f"Luffy Brain Offline ({e}). Ensure llama-server is running on 127.0.0.1:8081."

class LuffyHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass # Silent logging for performance

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"ready","companion":"Luffy OS"}')
        elif self.path == "/api/status":
            # Return phone telemetry
            bat_res = subprocess.run(["dumpsys", "battery"], capture_output=True, text=True).stdout
            level = "unknown"
            for line in bat_res.splitlines():
                if "level:" in line:
                    level = line.split(":", 1)[1].strip()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"battery": level, "engine": "llama.cpp", "model": "Qwen2.5-1.5B"}).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8")
        try:
            req_data = json.loads(body) if body else {}
        except Exception:
            req_data = {}

        if self.path == "/api/chat":
            user_msg = req_data.get("message", "").strip()
            if not user_msg:
                user_msg = "Hello Luffy"
            response_text = query_llama(user_msg)
            
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"reply": response_text}).encode("utf-8"))

        elif self.path == "/api/vision":
            query = req_data.get("query", "Summarize what is currently displayed on this screen.")
            # Read screen text hierarchy
            screen_context = ""
            try:
                dump_res = subprocess.run(["uiautomator", "dump", "/sdcard/screen_dump.xml"], capture_output=True, text=True, timeout=3)
                if os.path.exists("/sdcard/screen_dump.xml"):
                    with open("/sdcard/screen_dump.xml", "r", encoding="utf-8", errors="ignore") as f:
                        xml_content = f.read()
                    import re
                    texts = re.findall(r'text="([^"]+)"', xml_content)
                    non_empty = [t for t in texts if t.strip() and len(t.strip()) > 1]
                    screen_context = "\n".join(non_empty[:40])
            except Exception:
                screen_context = "Screen captured as raw framebuffer."

            vision_prompt = f"The user just captured their active phone screen. Screen text elements detected:\n---\n{screen_context}\n---\nUser query about this screen: '{query}'. Provide an accurate, helpful answer."
            vision_reply = query_llama(vision_prompt, max_tokens=300)

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"reply": vision_reply, "elements_found": len(screen_context.splitlines())}).encode("utf-8"))

        elif self.path == "/api/tool":
            action = req_data.get("action", "")
            res = execute_device_action(action)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"result": res}).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

def run_server():
    server = http.server.ThreadingHTTPServer((HOST, PORT), LuffyHandler)
    print(f"Luffy Core Server listening on http://{HOST}:{PORT}")
    server.serve_forever()

if __name__ == "__main__":
    run_server()
