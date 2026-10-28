#!/usr/bin/env python3
"""
Luffy OS Smart Autofill & Universal Form Engine
Scans current screen UI hierarchy, matches universal signup/login form fields,
injects user profile (Name, Email, Birthday, Phone, Username) + generates strong password,
and saves credentials to encrypted vault.

Trigger Rule: Only executes when the user wakes Luffy up and commands him to sign up/autofill.
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
                data = json.load(f)
                if isinstance(data, dict):
                    return data
        except Exception:
            pass
    return {
        "name": "Sireesh",
        "email": "sireeshmeets@gmail.com",
        "birthday": "02-04-2000",
        "phone": "+91",
        "username": "sireesh"
    }

def set_user_profile(profile_dict):
    profile = get_user_profile()
    profile.update(profile_dict)
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
    if not text:
        return
    subprocess.run(["su", "-c", f"input tap {cx} {cy}"])
    time.sleep(0.3)
    escaped = re.sub(r'([&|;$><`"\'\\])', r'\\\1', text)
    escaped = escaped.replace(" ", "%s")
    subprocess.run(["su", "-c", f"input text {escaped}"])
    time.sleep(0.3)

def detect_active_package_and_domain(root):
    pkg = ""
    for node in root.iter("node"):
        p = node.attrib.get("package", "")
        if p and not pkg:
            pkg = p
        res_id = node.attrib.get("resource-id", "").lower()
        txt = node.attrib.get("text", "").lower()
        if any(bar in res_id for bar in ["url_bar", "location_bar", "search_box", "address"]):
            if "." in txt:
                return txt.replace("https://", "").replace("http://", "").split("/")[0]
    if pkg:
        parts = pkg.split(".")
        return parts[1] + ".com" if len(parts) >= 2 else pkg
    return "unknown_service"

def autofill_signup(custom_profile=None):
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
    if custom_profile:
        profile.update(custom_profile)

    user_email = profile.get("email", "sireeshmeets@gmail.com")
    user_name = profile.get("name", "Sireesh")
    user_bday = profile.get("birthday", "02-04-2000")
    user_phone = profile.get("phone", "")
    user_handle = profile.get("username", "sireesh")
    domain = detect_active_package_and_domain(root)

    # Find all editable input fields
    fields = []
    for node in root.iter("node"):
        cls = node.attrib.get("class", "")
        res_id = node.attrib.get("resource-id", "").lower()
        is_focusable = node.attrib.get("focusable", "") == "true"
        if "EditText" in cls or (is_focusable and any(k in res_id for k in ["input", "text", "edit"])):
            bounds = node.attrib.get("bounds", "")
            cx, cy = parse_bounds(bounds)
            if cx is not None:
                fields.append({
                    "id": res_id,
                    "text": node.attrib.get("text", "").lower(),
                    "desc": node.attrib.get("content-desc", "").lower(),
                    "password": node.attrib.get("password", "") == "true",
                    "cx": cx,
                    "cy": cy
                })

    if not fields:
        return {"status": "error", "message": "No editable signup input fields found on this screen."}

    vault = LuffyVault()
    generated_pwd = vault.generate_password(16)

    filled_actions = []
    pwd_field_count = 0

    for f in fields:
        desc_all = f["id"] + " " + f["text"] + " " + f["desc"]
        is_pwd = f["password"] or any(k in desc_all for k in ["password", "pass", "pwd", "secret"])
        is_confirm = any(k in desc_all for k in ["confirm", "re-enter", "repeat", "verify"])
        is_bday = any(k in desc_all for k in ["birth", "dob", "bday", "date of birth", "birthday"])
        is_phone = any(k in desc_all for k in ["phone", "mobile", "tel", "cell"])
        is_email = any(k in desc_all for k in ["email", "mail", "e-mail"])

        if is_pwd:
            pwd_field_count += 1
            tap_and_type(f["cx"], f["cy"], generated_pwd)
            label = "Confirm Password" if (is_confirm or pwd_field_count > 1) else "Password"
            filled_actions.append(label)
        elif is_bday:
            tap_and_type(f["cx"], f["cy"], user_bday)
            filled_actions.append("Birthday")
        elif is_phone and user_phone:
            tap_and_type(f["cx"], f["cy"], user_phone)
            filled_actions.append("Phone")
        elif is_email:
            tap_and_type(f["cx"], f["cy"], user_email)
            filled_actions.append("Email")
        elif "first" in desc_all:
            first_name = user_name.split()[0] if " " in user_name else user_name
            tap_and_type(f["cx"], f["cy"], first_name)
            filled_actions.append("First Name")
        elif "last" in desc_all:
            last_name = user_name.split()[-1] if " " in user_name else ""
            tap_and_type(f["cx"], f["cy"], last_name)
            filled_actions.append("Last Name")
        elif "name" in desc_all and "user" not in desc_all:
            tap_and_type(f["cx"], f["cy"], user_name)
            filled_actions.append("Full Name")
        elif any(k in desc_all for k in ["user", "username", "handle", "nick"]):
            tap_and_type(f["cx"], f["cy"], user_handle)
            filled_actions.append("Username")
        else:
            # Fallback for generic text fields
            if len(filled_actions) == 0:
                tap_and_type(f["cx"], f["cy"], user_email)
                filled_actions.append("Email")

    # Store credentials into encrypted vault
    vault.add(domain, user_email, generated_pwd, email=user_email, notes="Auto-signup via Luffy with User Profile")

    return {
        "status": "success",
        "domain": domain,
        "email": user_email,
        "password": generated_pwd,
        "fields_filled": filled_actions,
        "message": f"Autofilled {', '.join(filled_actions)} for {domain}! Password securely saved to vault."
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
    if len(sys.argv) > 1 and sys.argv[1] == "login":
        res = autofill_login()
    else:
        res = autofill_signup()
    print(json.dumps(res, indent=2))
