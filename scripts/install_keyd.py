import subprocess

daemon_sh = """#!/system/bin/sh
# Luffy OS Hardware Key Event Daemon
# Direct hardware listening on /dev/input/event9 (gpio_keys)

LAST_CLICK=0
CLICK_COUNT=0
DOWN_TIME=0

getevent /dev/input/event9 | while read -r line; do
    case "$line" in
        *"0001 02bf 00000001"*)
            DOWN_TIME=$(date +%s)
            ;;
        *"0001 02bf 00000000"*)
            UP_TIME=$(date +%s)
            
            # Check for double-click window
            NOW_MS=$(date +%s)
            DIFF=$((NOW_MS - LAST_CLICK))
            
            if [ "$DIFF" -le 1 ] && [ "$CLICK_COUNT" -eq 1 ]; then
                # Double click detected -> Toggle E-Ink
                CLICK_COUNT=0
                LAST_CLICK=0
                CURR=$(settings get secure accessibility_display_daltonizer_enabled)
                if [ "$CURR" = "1" ]; then
                    settings put secure accessibility_display_daltonizer_enabled 0
                else
                    settings put secure accessibility_display_daltonizer 0
                    settings put secure accessibility_display_daltonizer_enabled 1
                fi
            else
                LAST_CLICK=$NOW_MS
                CLICK_COUNT=1
                # Trigger single-click action after short delay if not interrupted
                (
                    sleep 1
                    # Launch Luffy Vision
                    am start -n com.luffy.core/.LuffyMainActivity --activity-clear-top
                ) &
            fi
            ;;
    esac
done
"""

# Push and install in Magisk service.d
p = subprocess.Popen(["python", "d:/PortfolioHub/Note 9 Revival/run_root.py", f"""
cat << 'EOF' > /data/adb/service.d/luffy_keyd.sh
{daemon_sh}
EOF
chmod 755 /data/adb/service.d/luffy_keyd.sh
nohup sh /data/adb/service.d/luffy_keyd.sh > /dev/null 2>&1 &
ps -ef | grep luffy_keyd
"""], stdout=subprocess.PIPE, text=True)
out, err = p.communicate()
print(out)
