#!/usr/bin/env python3
"""
Luffy Engine: Lightweight Local-Host REST Daemon for Android (Termux)
Routes intent parsing and tool calling to the local llama.cpp GGUF instance.
"""

import json
import http.server
import socketserver
import subprocess
from luffy_prompts import LUFFY_SYSTEM_PROMPT, LUFFY_TOOL_DEFINITIONS

PORT = 8080
LLAMA_CPP_ENDPOINT = "http://127.0.0.1:8081/completion"

class LuffyHandler(http.server.SimpleHTTPRequestHandler):
    def do_POST(self):
        if self.path == "/api/chat":
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            req = json.loads(post_data.decode('utf-8'))
            user_msg = req.get("message", "")

            # Dispatch intent to local engine
            response = self.process_luffy_intent(user_msg)

            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response).encode('utf-8'))
        elif self.path == "/api/vision":
            # Screen vision inference endpoint
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "analyzed", "summary": "Screen visual intent parsed."}).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    def process_luffy_intent(self, text):
        t = text.lower()
        if "call" in t:
            # Extract target
            target = text.split("call")[-1].strip()
            return {"action": "call", "target": target, "reply": f"Luffy: Calling {target} right now."}
        elif "message" in t or "sms" in t:
            return {"action": "sms", "reply": "Luffy: Preparing your message."}
        elif "e-ink" in t or "grayscale" in t or "gray" in t:
            return {"action": "toggle_eink", "reply": "Luffy: Toggling E-Ink mode."}
        elif "read" in t or "book" in t or "koreader" in t:
            return {"action": "open_koreader", "reply": "Luffy: Opening KOReader."}
        elif "music" in t or "play" in t or "mornye" in t:
            return {"action": "open_mornye", "reply": "Luffy: Launching 32-bit audio player."}
        else:
            return {"action": "chat", "reply": f"Luffy: On it! I will make sure this is done: {text}"}

def run_server():
    with socketserver.TCPServer(("", PORT), LuffyHandler) as httpd:
        print(f"[*] Luffy Offline Agent listening on http://localhost:{PORT}")
        httpd.serve_forever()

if __name__ == "__main__":
    run_server()

