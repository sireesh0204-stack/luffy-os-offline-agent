#!/system/bin/sh
# Luffy AI Daemon Service - Starts on boot via Magisk service.d
# Auto-Healing Watchdog for llama-server & luffy_server.py

# Fix Samsung Note 9 Low-Brightness OLED Glitch (Disable CABC)
if [ -f /sys/class/lcd/panel/adaptive_control ]; then
    echo 0 > /sys/class/lcd/panel/adaptive_control
fi

# Luffy OS Branding in Settings > About Phone
settings put global device_name "Luffy OS Core"
resetprop ro.build.display.id "Luffy OS Core v1.0"
resetprop ro.product.model "Note 9 (Luffy OS Core)"
resetprop ro.build.flavor "LuffyOS-crownlte"

# Ensure Termux permissions
export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export LD_LIBRARY_PATH=$PREFIX/lib
export PATH=$PREFIX/bin:$PATH
export TMPDIR=$PREFIX/tmp

# Wait for Android system to stabilize
sleep 6

# Continuous background watchdog: auto-heals and resurrects daemons if killed
(
    while true; do
        if ! pgrep -f "llama-server" >/dev/null; then
            su u0_a259 -g 10259 -G 3003 -c "export PREFIX=/data/data/com.termux/files/usr; export HOME=/data/data/com.termux/files/home; export LD_LIBRARY_PATH=\$PREFIX/lib; export PATH=\$PREFIX/bin:\$PATH; nohup \$PREFIX/bin/llama-server -m \$HOME/models/qwen2.5-1.5b-instruct-q4_k_m.gguf --host 127.0.0.1 --port 8081 -c 2048 -t 4 --log-disable >/dev/null 2>&1 &"
        fi

        if ! pgrep -f "luffy_server.py" >/dev/null; then
            su u0_a259 -g 10259 -G 3003 -c "export PREFIX=/data/data/com.termux/files/usr; export HOME=/data/data/com.termux/files/home; export LD_LIBRARY_PATH=\$PREFIX/lib; export PATH=\$PREFIX/bin:\$PATH; nohup \$PREFIX/bin/python \$HOME/luffy_server.py >/dev/null 2>&1 &"
        fi

        if ! pgrep -f "luffy_keyd.sh" >/dev/null; then
            nohup busybox sh /data/adb/service.d/luffy_keyd.sh >/dev/null 2>&1 &
        fi

        sleep 8
    done
) &
