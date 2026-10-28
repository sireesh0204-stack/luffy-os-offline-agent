#!/system/bin/sh
# Luffy OS Kernel Key Event Daemon
# Hardware Bixby Button State-Machine:
# - Single Click: Turn ON/OFF Ambient Music (AmbientHudActivity fluid HUD)
# - Double Click: Silent Screenshot & Screen Analyze (Luffy Vision)
# - Long Hold: Voice Assistant (while held)

PIDFILE="/data/local/tmp/luffy_keyd.pid"
TAP_FILE="/data/local/tmp/luffy_tap_pending"
HOLD_FLAG="/data/local/tmp/luffy_held"

# Single-instance enforcement
if [ -f "$PIDFILE" ]; then
    OLDPID=$(cat "$PIDFILE" 2>/dev/null)
    if [ -n "$OLDPID" ] && kill -0 "$OLDPID" 2>/dev/null && [ "$OLDPID" != "$$" ]; then
        exit 0
    fi
fi
echo $$ > "$PIDFILE"

rm -f "$TAP_FILE" "$HOLD_FLAG"
HOLD_PID=""
IS_DOUBLE=0

# Clean up any orphaned getevent listeners
pkill -9 -f "getevent /dev/input/event9" 2>/dev/null

getevent /dev/input/event9 | while read -r line; do
    case "$line" in
        *"0001 02bf 00000001"*)
            # KEY DOWN
            rm -f "$HOLD_FLAG"
            if [ -f "$TAP_FILE" ]; then
                # Second tap arrived before debounce timer expired -> DOUBLE CLICK!
                rm -f "$TAP_FILE"
                IS_DOUBLE=1
            else
                IS_DOUBLE=0
                # Start 380ms hold timer
                (
                    sleep 0.38
                    touch "$HOLD_FLAG"
                    am start -n com.luffy.core/.LuffyMainActivity --es trigger "long_hold" --activity-clear-top >/dev/null 2>&1
                ) &
                HOLD_PID=$!
            fi
            ;;

        *"0001 02bf 00000000"*)
            # KEY UP
            if [ -n "$HOLD_PID" ]; then
                kill "$HOLD_PID" 2>/dev/null
                HOLD_PID=""
            fi

            if [ -f "$HOLD_FLAG" ]; then
                # Was already handled as a long hold
                rm -f "$HOLD_FLAG"
            elif [ "$IS_DOUBLE" = "1" ]; then
                # Double click confirmed on second key release
                IS_DOUBLE=0
                am start -n com.luffy.core/.LuffyMainActivity --es trigger "vision" --activity-clear-top >/dev/null 2>&1
            else
                # First tap released before 380ms -> start debounce timer for single click
                touch "$TAP_FILE"
                (
                    sleep 0.28
                    if [ -f "$TAP_FILE" ]; then
                        rm -f "$TAP_FILE"
                        # SINGLE CLICK: Toggle Ambient Music HUD
                        am start -n com.luffy.core/.AmbientHudActivity --activity-clear-top --activity-no-animation >/dev/null 2>&1
                    fi
                ) &
            fi
            ;;
    esac
done
