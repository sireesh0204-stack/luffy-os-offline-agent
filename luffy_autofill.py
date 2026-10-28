#!/usr/bin/env python3
"""
Luffy OS Smart Autofill & Form Assistant
Scans current screen UI hierarchy, detects signup / login forms,
auto-injects user details + generates strong password, and saves to encrypted vault.
"""

import os
import re
import sys
import time
import json
import subprocess
import xml.etree.ElementTree as ET
from luffy_vault import LuffyVault

USER_PROFILE_PATH = os.path.expanduser("/data/data/com.termux/files/home/.luffy_profile.json")

def get_user_profile():
    if os.path.exists(USER_PROFILE_PATH):
        try:
            with open(USER_PROFILE_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    # Default profile template
    return {
        "name": "Sireesh",
        "email": "sireeshmeets@gmail.com",
        "username": "sireesh"
    }

def set_user_profile(name, email, username=None):
    profile = {
        "name": name,
        "email": email,
        "username": username or email.split("@")[0]
    }
    with open(USER_PROFILE_PATH, "w", encoding="utf-8") as f:
        json.dump(profile, f, indent=2)
    return profile

def dump_ui():
    xml_path = "/data/local/tmp/luffy_autofill_dump.xml"
    subprocess.run(["su", "-c", f"rm -f {xml_path} && uiautomator dump {xml_path} && chmod 666 {xml_path}"], capture_output=True, timeout=8)
    return xml_path if os.path.exists(xml_path) else None

def parse_bounds(bounds_str):
    m = re.findall(r"\[(\d+),(\d+)\]", bounds_str)
    if len(m) == 2:
        x1, y1 = int(m[0][0]), int(m[0][1])
        x2, y2 = int(m[1][0]), int(m[1][1])
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None, None

def tap_and_type(cx, cy, text):
    subprocess.run(["su", "-c", f"input tap {cx} {cy}"])
    time.sleep(0.3)
    escaped = re.sub(r'([&|;$><`"\'\\])', r'\\\1', text)
    escaped = escaped.replace(" ", "%s")
    subprocess.run(["su", "-c", f"input text {escaped}"])
    time.sleep(0.3)

def detect_active_package_and_domain(root):
    # Look for browser URL bar or app package
    pkg = ""
    domain = "unknown_app"
    for node in root.iter("node"):
        p = node.attrib.get("package", "")
        if p and not pkg:
            pkg = p
        res_id = node.attrib.get("resource-id", "").lower()
        txt = node.attrib.get("text", "").lower()
        if "url_bar" in res_id or "location_bar" in res_id or "search_box" in res_id or "address" in res_id:
            if "." in txt:
                domain = txt.replace("https://", "").replace("http://", "").split("/")[0]
                return domain
    if pkg:
        # e.g. com.github.android -> github.com
        parts = pkg.split(".")
        if len(parts) >= 2:
            domain = parts[1] + ".com"
        else:
            domain = pkg
    return domain

def autofill_signup(custom_email=None, custom_name=None):
    xml_path = dump_ui()
    if not xml_path:
        return {"status": "error", "message": "Failed to capture screen hierarchy."}

    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
    except Exception as e:
        return {"status": "error", "message": f"XML parse error: {e}"}
    finally:
        if os.path.exists(xml_path):
            try:
                os.remove(xml_path)
            except Exception:
                pass

    profile = get_user_profile()
    user_email = custom_email or profile.get("email", "sireesh@gmail.com")
    user_name = custom_name or profile.get("name", "Sireesh")
    domain = detect_active_package_and_domain(root)

    # Find all EditText / input fields
    fields = []
    for node in root.iter("node"):
        cls = node.attrib.get("class", "")
        if "EditText" in cls or node.attrib.get("focusable", "") == "true" and "input" in node.attrib.get("resource-id", "").lower():
            bounds = node.attrib.get("bounds", "")
            cx, cy = parse_bounds(bounds)
            if cx is not None:
                fields.append({
                    "id": node.attrib.get("resource-id", "").lower(),
                    "text": node.attrib.get("text", "").lower(),
                    "desc": node.attrib.get("content-desc", "").lower(),
                    "password": node.attrib.get("password", "") == "true",
                    "cx": cx,
                    "cy": cy
                })

    if not fields:
        return {"status": "error", "message": "No editable input fields found on this screen."}

    vault = LuffyVault()
    generated_pwd = vault.generate_password(16)

    # Categorize fields
    filled_actions = []
    pwd_field_count = 0

    for f in fields:
        desc_all = f["id"] + " " + f["text"] + " " + f["desc"]
        is_pwd = f["password"] or "password" in desc_all or "pass" in desc_all or "pwd" in desc_all
        is_confirm = "confirm" in desc_all or "re-enter" in desc_all or "repeat" in desc_all

        if is_pwd:
            pwd_field_count += 1
            tap_and_type(f["cx"], f["cy"], generated_pwd)
            label = "Confirm Password" if (is_confirm or pwd_field_count > 1) else "Password"
            filled_actions.append(label)
        elif "email" in desc_all or "mail" in desc_all:
            tap_and_type(f["cx"], f["cy"], user_email)
            filled_actions.append("Email")
        elif "name" in desc_all and "user" not in desc_all:
            tap_and_type(f["cx"], f["cy"], user_name)
            filled_actions.append("Name")
        elif "user" in desc_all or "login" in desc_all or "account" in desc_all:
            tap_and_type(f["cx"], f["cy"], user_email)
            filled_actions.append("Username/Email")
        else:
            # Fallback for generic text fields
            if len(filled_actions) == 0:
                tap_and_type(f["cx"], f["cy"], user_email)
                filled_actions.append("Email")

    # Save newly generated account to vault
    vault.add(domain, user_email, generated_pwd, email=user_email, notes="Auto-signup via Luffy")

    return {
        "status": "success",
        "domain": domain,
        "email": user_email,
        "password": generated_pwd,
        "fields_filled": filled_actions,
        "message": f"Successfully autofilled {', '.join(filled_actions)} for {domain}! Password securely saved to vault."
    }

def autofill_login():
    xml_path = dump_ui()
    if not xml_path:
        return {"status": "error", "message": "Failed to capture screen hierarchy."}

    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
    except Exception as e:
        return {"status": "error", "message": f"XML parse error: {e}"}
    finally:
        if os.path.exists(xml_path):
            try:
                os.remove(xml_path)
            except Exception:
                pass

    domain = detect_active_package_and_domain(root)
    vault = LuffyVault()
    account = vault.get(domain)
    if not account:
        return {"status": "error", "message": f"No saved credentials found for {domain} in vault."}

    fields = []
    for node in root.iter("node"):
        cls = node.attrib.get("class", "")
        if "EditText" in cls or node.attrib.get("focusable", "") == "true":
            bounds = node.attrib.get("bounds", "")
            cx, cy = parse_bounds(bounds)
            if cx is not None:
                fields.append({
                    "id": node.attrib.get("resource-id", "").lower(),
                    "text": node.attrib.get("text", "").lower(),
                    "desc": node.attrib.get("content-desc", "").lower(),
                    "password": node.attrib.get("password", "") == "true",
                    "cx": cx,
                    "cy": cy
                })

    for f in fields:
        desc_all = f["id"] + " " + f["text"] + " " + f["desc"]
        if f["password"] or "pass" in desc_all:
            tap_and_type(f["cx"], f["cy"], account["password"])
        else:
            tap_and_type(f["cx"], f["cy"], account.get("username") or account.get("email"))

    return {
        "status": "success",
        "domain": domain,
        "username": account.get("username"),
        "message": f"Autofilled credentials for {domain} from vault."
    }

if __name__ == "__main__":
    import json
    if len(sys.argv) > 1 and sys.argv[1] == "login":
        res = autofill_login()
    else:
        res = autofill_signup()
    print(json.dumps(res, indent=2))
