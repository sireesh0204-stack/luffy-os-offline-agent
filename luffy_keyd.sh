#!/system/bin/sh
# Luffy OS Kernel Key Event Daemon
# Hardware Bixby Button Mapping:
# - Single Click: Turn ON/OFF Ambient Music (AmbientHudActivity fluid HUD)
# - Double Click: Silent Screenshot & Screen Analyze (Luffy Vision)
# - Long Hold: Voice Assistant (while held)

TIMER_PID=""
HOLD_PID=""
rm -f /data/local/tmp/luffy_held

getevent /dev/input/event9 | while read -r line; do
    case "$line" in
        *"0001 02bf 00000001"*)
            # KEY DOWN: Start hold timer
            rm -f /data/local/tmp/luffy_held
            if [ -n "$HOLD_PID" ]; then
                kill "$HOLD_PID" 2>/dev/null
            fi
            (
                sleep 0.38
                touch /data/local/tmp/luffy_held
                # Long Hold: Wake Voice Assistant WHILE user is holding button
                am start -n com.luffy.core/.LuffyMainActivity --es trigger "long_hold" --activity-clear-top >/dev/null 2>&1
            ) &
            HOLD_PID=$!
            ;;

        *"0001 02bf 00000000"*)
            # KEY UP
            # Cancel hold timer if released early
            if [ -n "$HOLD_PID" ]; then
                kill "$HOLD_PID" 2>/dev/null
                HOLD_PID=""
            fi

            if [ -f /data/local/tmp/luffy_held ]; then
                # Hold was already triggered while holding down -> Swallow release cleanly
                rm -f /data/local/tmp/luffy_held
            else
                # Released before 380ms -> Short tap
                if [ -n "$TIMER_PID" ] && kill -0 "$TIMER_PID" 2>/dev/null; then
                    # Second tap arrived before timer expired -> Double click!
                    kill "$TIMER_PID" 2>/dev/null
                    TIMER_PID=""

                    # DOUBLE CLICK: Silent Screenshot & Screen Analyze (Luffy Vision)
                    am start -n com.luffy.core/.LuffyMainActivity --es trigger "vision" --activity-clear-top >/dev/null 2>&1
                else
                    # First tap -> Start 260ms single-click timer
                    (
                        sleep 0.26
                        # SINGLE CLICK: Turn ON/OFF Ambient Music Feature (Fluid HUD)
                        am start -n com.luffy.core/.AmbientHudActivity --activity-clear-top --activity-no-animation >/dev/null 2>&1
                    ) &
                    TIMER_PID=$!
                fi
            fi
            ;;
    esac
done
