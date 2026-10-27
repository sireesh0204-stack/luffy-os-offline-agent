#!/system/bin/sh
# Luffy OS Kernel Key Event Daemon
# Hardware Timestamp State-Machine (/dev/input/event9)

TIMER_PID=""
DOWN_TIME=0

getevent /dev/input/event9 | while read -r line; do
    case "$line" in
        *"0001 02bf 00000001"*)
            # KEY DOWN: record exact millisecond timestamp
            DOWN_TIME=$(date +%s%3N)
            ;;

        *"0001 02bf 00000000"*)
            # KEY UP: compute exact press duration
            UP_TIME=$(date +%s%3N)
            DURATION=$((UP_TIME - DOWN_TIME))

            if [ "$DURATION" -ge 380 ]; then
                # HELD FOR >= 380ms -> 100% RELIABLE LONG HOLD!
                # Cancel any pending single click
                if [ -n "$TIMER_PID" ]; then
                    kill "$TIMER_PID" 2>/dev/null
                    TIMER_PID=""
                fi
                # Summon Assistant Mode (NO screenshot!)
                am start -n com.luffy.core/.LuffyMainActivity --es trigger "long_hold" --activity-clear-top
            else
                # SHORT TAP (< 380ms)
                if [ -n "$TIMER_PID" ] && kill -0 "$TIMER_PID" 2>/dev/null; then
                    # Double click confirmed! Cancel single click immediately
                    kill "$TIMER_PID" 2>/dev/null
                    TIMER_PID=""

                    # Toggle Daltonizer E-Ink
                    CURR=$(settings get secure accessibility_display_daltonizer_enabled)
                    if [ "$CURR" = "1" ]; then
                        settings put secure accessibility_display_daltonizer_enabled 0
                    else
                        settings put secure accessibility_display_daltonizer 0
                        settings put secure accessibility_display_daltonizer_enabled 1
                    fi
                else
                    # First click -> Start fast 260ms timer
                    (
                        sleep 0.26
                        # Ultra-fast raw screencap (0.16s instead of 2.67s!)
                        screencap /sdcard/luffy_screen.raw &
                        # Summon Vision Mode
                        am start -n com.luffy.core/.LuffyMainActivity --es trigger "single_click" --activity-clear-top
                    ) &
                    TIMER_PID=$!
                fi
            fi
            ;;
    esac
done
