import os
import subprocess
import zipfile
import shutil

SDK = r"C:\Users\siree\AppData\Local\Android\Sdk"
BUILD_TOOLS = os.path.join(SDK, "build-tools", "34.0.0")
AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS, "d8.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat")
ANDROID_JAR = os.path.join(SDK, "platforms", "android-34", "android.jar")

os.chdir(r"D:\PortfolioHub\Note 9 Revival\LuffyCore")

for d in ["build", "gen", "bin"]:
    if os.path.exists(d):
        shutil.rmtree(d)
    os.makedirs(d)

print("[1/6] Compiling resources...")
subprocess.check_call([AAPT2, "compile", "--dir", "res", "-o", "build/compiled_res.zip"])

print("[2/6] Linking APK and generating R.java...")
subprocess.check_call([
    AAPT2, "link",
    "-I", ANDROID_JAR,
    "build/compiled_res.zip",
    "--manifest", "AndroidManifest.xml",
    "-o", "build/unaligned.apk",
    "--java", "gen"
])

print("[3/6] Compiling Java classes...")
java_files = []
for root, _, files in os.walk("src"):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))
for root, _, files in os.walk("gen"):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))

subprocess.check_call(["javac", "-encoding", "UTF-8", "-cp", f"{ANDROID_JAR};gen", "-d", "bin"] + java_files)

print("[4/6] Converting to DEX (d8)...")
class_files = []
for root, _, files in os.walk("bin"):
    for f in files:
        if f.endswith(".class"):
            class_files.append(os.path.join(root, f))

subprocess.check_call([D8, "--output", "build", "--min-api", "28"] + class_files, shell=True)

print("[5/6] Packaging classes.dex into APK...")
with zipfile.ZipFile("build/unaligned.apk", "a") as z:
    z.write("build/classes.dex", "classes.dex")

print("[6/6] Zipaligning and signing...")
subprocess.check_call([ZIPALIGN, "-p", "-f", "4", "build/unaligned.apk", "LuffyCore.apk"])

# Generate debug keystore if not exists
KEYSTORE = "debug.keystore"
if not os.path.exists(KEYSTORE):
    subprocess.check_call([
        "keytool", "-genkeypair", "-keystore", KEYSTORE,
        "-storepass", "android", "-alias", "androiddebugkey",
        "-keypass", "android", "-dname", "CN=Android Debug,O=Android,C=US",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000"
    ])

subprocess.check_call([
    APKSIGNER, "sign",
    "--ks", KEYSTORE,
    "--ks-pass", "pass:android",
    "--key-pass", "pass:android",
    "--out", "LuffyCore_signed.apk",
    "LuffyCore.apk"
], shell=True)

print("SUCCESS: LuffyCore_signed.apk ready!")

