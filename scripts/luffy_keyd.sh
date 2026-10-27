#!/system/bin/sh
# Luffy OS Kernel Key Event Daemon
# Direct /dev/input/event9 state machine with PID kill cancellation

TIMER_PID=""
HOLD_PID=""
rm -f /data/local/tmp/luffy_hold

getevent /dev/input/event9 | while read -r line; do
    case "$line" in
        *"0001 02bf 00000001"*)
            # KEY DOWN
            if [ -n "$HOLD_PID" ]; then
                kill "$HOLD_PID" 2>/dev/null
            fi
            (
                sleep 0.45
                touch /data/local/tmp/luffy_hold
                am start -n com.luffy.core/.LuffyMainActivity --es trigger "long_hold" --activity-clear-top
            ) &
            HOLD_PID=$!
            ;;

        *"0001 02bf 00000000"*)
            # KEY UP
            if [ -n "$HOLD_PID" ]; then
                kill "$HOLD_PID" 2>/dev/null
                HOLD_PID=""
            fi

            if [ -f /data/local/tmp/luffy_hold ]; then
                # Long hold was triggered, swallow release completely
                rm -f /data/local/tmp/luffy_hold
                if [ -n "$TIMER_PID" ]; then
                    kill "$TIMER_PID" 2>/dev/null
                    TIMER_PID=""
                fi
            else
                # Short press release
                if [ -n "$TIMER_PID" ] && kill -0 "$TIMER_PID" 2>/dev/null; then
                    # Second click arrived before timer expired -> DOUBLE CLICK!
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
                    # First click -> Start timer for single click
                    (
                        sleep 0.35
                        # Silent clean screenshot FIRST
                        screencap -p /sdcard/luffy_screen.png
                        # Open Luffy Vision
                        am start -n com.luffy.core/.LuffyMainActivity --activity-clear-top
                    ) &
                    TIMER_PID=$!
                fi
            fi
            ;;
    esac
done
