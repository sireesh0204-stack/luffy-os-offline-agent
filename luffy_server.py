#!/usr/bin/env python3
"""
Luffy OS Core Companion Server
Runs natively on Samsung Galaxy Note 9 on 127.0.0.1:8080.
Connects to local llama-server on 127.0.0.1:8081 for offline neural inference.
Provides device tool execution (calling, messaging, E-Ink toggle, app launch, screen vision,
offline password vault & biometric autofill).
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
from luffy_vault import LuffyVault
import luffy_autofill

HOST = "127.0.0.1"
PORT = 8080
LLAMA_URL = "http://127.0.0.1:8081/completion"

SYSTEM_PROMPT = """You are Luffy, an offline, highly intelligent, fiercely loyal AI companion running natively on Samsung Galaxy Note 9 (Luffy OS).
Your prime directive: Complete what the user asks for. You may ask concise clarifying questions if essential, but your main goal is always goal-completion.
You manage the user's personal offline password vault stored securely on this device.
When the user asks for their saved credentials or passwords, you must look them up using [ACTION:VAULT_GET domain].
When the user asks to sign up or fill a form, use [ACTION:SIGNUP_FILL].
When the user asks to log in, use [ACTION:LOGIN_FILL].
Keep responses concise, natural, direct, and conversational. No corporate fluff.
Available phone actions:
- [ACTION:CALL number]
- [ACTION:SMS number | message]
- [ACTION:EINK toggle]
- [ACTION:LAUNCH package_name]
- [ACTION:TIMER seconds]
- [ACTION:SIGNUP_FILL]
- [ACTION:LOGIN_FILL]
- [ACTION:VAULT_GET domain]
- [ACTION:VAULT_ADD domain | user | pass]
- [ACTION:VAULT_GEN]
"""

def execute_device_action(action_str):
    try:
        action_clean = action_str.strip()
        if ":" in action_clean:
            parts = action_clean.split(":", 1)
            cmd_type = parts[0].strip().upper()
            args = parts[1].strip()
        elif " " in action_clean:
            parts = action_clean.split(" ", 1)
            cmd_type = parts[0].strip().upper()
            args = parts[1].strip()
        else:
            cmd_type = action_clean.upper()
            args = ""

        # Clean args like platform:github -> github
        if ":" in args and " " not in args:
            args = args.split(":", 1)[1].strip()

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
        elif cmd_type == "SIGNUP_FILL":
            res = luffy_autofill.autofill_signup()
            return res.get("message", "Signup form filled.")
        elif cmd_type in ("LOGIN_FILL", "LOGIN"):
            res = luffy_autofill.autofill_login()
            return res.get("message", "Login autofilled.")
        elif cmd_type == "VAULT_GET":
            acc = LuffyVault().get(args)
            if acc:
                return f"Vault for {acc['domain']}: Username: {acc['username']}, Password: {acc['password']}"
            return f"No vault entry found for '{args}'."
        elif cmd_type == "VAULT_GEN":
            pwd = LuffyVault.generate_password(18)
            return f"Generated strong password: {pwd}"
        elif cmd_type == "VAULT_ADD":
            p = [x.strip() for x in args.split("|")]
            if len(p) >= 3:
                LuffyVault().add(p[0], p[1], p[2])
                return f"Saved credentials for {p[0]} in encrypted vault."
            return "Usage: VAULT_ADD domain | user | pass"
    except Exception as e:
        return f"Action error: {e}"
    return None

def ensure_llama_running():
    try:
        with urllib.request.urlopen("http://127.0.0.1:8081/health", timeout=1) as resp:
            return True
    except Exception:
        pass
    cmd = (
        "export PREFIX=/data/data/com.termux/files/usr; "
        "export HOME=/data/data/com.termux/files/home; "
        "export LD_LIBRARY_PATH=$PREFIX/lib; "
        "export PATH=$PREFIX/bin:$PATH; "
        "nohup $PREFIX/bin/llama-server "
        "-m $HOME/models/qwen2.5-1.5b-instruct-q4_k_m.gguf "
        "--host 127.0.0.1 --port 8081 -c 2048 -t 4 --log-disable >/dev/null 2>&1 &"
    )
    subprocess.Popen(["sh", "-c", cmd])
    for _ in range(10):
        time.sleep(0.5)
        try:
            with urllib.request.urlopen("http://127.0.0.1:8081/health", timeout=1) as resp:
                return True
        except Exception:
            pass
    return False

def query_llama(prompt, max_tokens=256, temperature=0.7):
    ensure_llama_running()
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
            bat_res = subprocess.run(["dumpsys", "battery"], capture_output=True, text=True).stdout
            level = "unknown"
            for line in bat_res.splitlines():
                if "level:" in line:
                    level = line.split(":", 1)[1].strip()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"battery": level, "engine": "llama.cpp", "model": "Qwen2.5-1.5B"}).encode("utf-8"))
        elif self.path == "/api/vault/list":
            vault = LuffyVault()
            accounts = vault.list_all()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "accounts": accounts}).encode("utf-8"))
        elif self.path == "/api/vault/generate":
            pwd = LuffyVault.generate_password(18)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "password": pwd}).encode("utf-8"))
        elif self.path == "/api/profile":
            profile = luffy_autofill.get_user_profile()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "profile": profile}).encode("utf-8"))
        elif self.path == "/api/apps":
            # List 3rd party user installed apps
            try:
                res = subprocess.run(["pm", "list", "packages", "-3"], capture_output=True, text=True)
                pkgs = [line.replace("package:", "").strip() for line in res.stdout.splitlines() if line.strip()]
            except Exception:
                pkgs = []
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "apps": pkgs}).encode("utf-8"))
        elif self.path == "/api/screen/inspect":
            # Return live screen perception
            tmp_img = "/data/local/tmp/luffy_inspect.png"
            ocr_text = ""
            try:
                subprocess.run(["screencap", "-p", tmp_img], capture_output=True, timeout=3)
                if os.path.exists(tmp_img):
                    env = os.environ.copy()
                    env["PATH"] = "/data/data/com.termux/files/usr/bin:" + env.get("PATH", "")
                    env["LD_LIBRARY_PATH"] = "/data/data/com.termux/files/usr/lib"
                    res = subprocess.run(
                        ["/data/data/com.termux/files/usr/bin/tesseract", tmp_img, "stdout", "--oem", "1", "-l", "eng"],
                        capture_output=True, text=True, env=env, timeout=6
                    )
                    ocr_text = res.stdout.strip()
            except Exception:
                ocr_text = ""
            finally:
                if os.path.exists(tmp_img):
                    try:
                        os.remove(tmp_img)
                    except Exception:
                        pass
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "status": "ok",
                "text": ocr_text or "[No readable text on current screen]",
                "length": len(ocr_text)
            }).encode("utf-8"))
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
            ocr_text = req_data.get("mock_ocr") or req_data.get("ocr_text", "")
            
            # Step 1: Capture screenshot to temporary ramdisk path if no text provided
            tmp_img = "/data/local/tmp/luffy_ocr.png"
            if not ocr_text:
                try:
                    subprocess.run(["screencap", "-p", tmp_img], capture_output=True, timeout=3)
                    if os.path.exists(tmp_img):
                        # Step 2: Extract text using Tesseract OCR
                        env = os.environ.copy()
                        env["PATH"] = "/data/data/com.termux/files/usr/bin:" + env.get("PATH", "")
                        env["LD_LIBRARY_PATH"] = "/data/data/com.termux/files/usr/lib"
                        res = subprocess.run(
                            ["/data/data/com.termux/files/usr/bin/tesseract", tmp_img, "stdout", "--oem", "1", "-l", "eng"],
                            capture_output=True,
                            text=True,
                            env=env,
                            timeout=6
                        )
                        ocr_text = res.stdout.strip()
                except Exception as e:
                    ocr_text = ""
                finally:
                    if os.path.exists(tmp_img):
                        try:
                            os.remove(tmp_img)
                        except Exception:
                            pass


            if len(ocr_text) > 5000:
                ocr_text = ocr_text[:5000] + "\n...[truncated]"

            if not ocr_text:
                ocr_text = "[No legible text detected on screen]"

            ocr_lower = ocr_text.lower()
            has_question = ("?" in ocr_text) or any(
                kw in ocr_lower for kw in [
                    "what is", "which of", "how to", "who is", "where is", "why is",
                    "calculate", "solve", "choose the", "correct option", "select the",
                    "what does", "when did", "true or false"
                ]
            )

            if has_question:
                vision_prompt = (
                    f"You are Luffy, offline intelligence on Samsung Note 9. The screen contains a question or test problem:\n"
                    f"---\n{ocr_text}\n---\n"
                    f"Instructions: Solve the question step by step. Conclude strictly with:\n"
                    f"FINAL_ANSWER: <exact option letter and value>\n"
                    f"EXPLANATION: <1 sentence explanation>"
                )
                raw_reply = query_llama(vision_prompt, max_tokens=180, temperature=0.1)
                if "FINAL_ANSWER:" in raw_reply:
                    parts = raw_reply.split("FINAL_ANSWER:")
                    ans_and_rest = parts[1].strip()
                    if "EXPLANATION:" in ans_and_rest:
                        ans_part, exp_part = ans_and_rest.split("EXPLANATION:", 1)
                        vision_reply = f"Answer: {ans_part.strip()}\n\n{exp_part.strip()}"
                    else:
                        vision_reply = f"Answer: {ans_and_rest}"
                else:
                    vision_reply = raw_reply


            else:
                vision_prompt = (
                    f"You are Luffy, offline phone companion. The user captured their active screen.\n"
                    f"OCR Text detected from screen:\n---\n{ocr_text}\n---\n"
                    f"User request: '{query}'. Provide a crisp, concise summary (max 2-3 bullet points or 2 sentences)."
                )
                vision_reply = query_llama(vision_prompt, max_tokens=150, temperature=0.3)


            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "reply": vision_reply,
                "text_length": len(ocr_text),
                "extracted_snippet": ocr_text[:200]
            }).encode("utf-8"))

        elif self.path == "/api/vault/get":
            domain = req_data.get("domain", "")
            vault = LuffyVault()
            acc = vault.get(domain)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            if acc:
                self.wfile.write(json.dumps({"status": "ok", "account": acc}).encode("utf-8"))
            else:
                self.wfile.write(json.dumps({"status": "not_found", "message": f"No credentials found for {domain}"}).encode("utf-8"))

        elif self.path == "/api/vault/add":
            domain = req_data.get("domain", "")
            username = req_data.get("username", "")
            password = req_data.get("password", "")
            email = req_data.get("email", "")
            notes = req_data.get("notes", "")
            vault = LuffyVault()
            vault.add(domain, username, password, email=email, notes=notes)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "message": f"Saved {domain}"}).encode("utf-8"))

        elif self.path == "/api/vault/delete":
            domain = req_data.get("domain", "")
            vault = LuffyVault()
            success = vault.delete(domain)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok" if success else "not_found"}).encode("utf-8"))

        elif self.path == "/api/profile":
            updated = luffy_autofill.set_user_profile(req_data)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "profile": updated}).encode("utf-8"))

        elif self.path == "/api/vault/import":
            csv_path = req_data.get("csv_path", "")
            vault = LuffyVault()
            count, msg = vault.import_csv(csv_path)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "imported": count, "message": msg}).encode("utf-8"))

        elif self.path == "/api/autofill/signup":
            res = luffy_autofill.autofill_signup(
                custom_email=req_data.get("email"),
                custom_name=req_data.get("name")
            )
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(res).encode("utf-8"))

        elif self.path == "/api/autofill/login":
            res = luffy_autofill.autofill_login()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(res).encode("utf-8"))

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
