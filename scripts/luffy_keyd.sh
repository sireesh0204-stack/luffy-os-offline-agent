#!/system/bin/sh
# Luffy OS Kernel Key Event Daemon
# Active Hold Trigger & Double/Single Click State-Machine

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
                # Wake Voice Assistant WHILE user is holding button!
                am start -n com.luffy.core/.LuffyMainActivity --es trigger "long_hold" --activity-clear-top
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

                    # DOUBLE CLICK: Silent Screen Vision Analysis (Screenshot -> Tesseract OCR -> Qwen Summary)
                    am start -n com.luffy.core/.LuffyMainActivity --es trigger "single_click" --activity-clear-top >/dev/null 2>&1
                else
                    # First tap -> Start 260ms single-click timer
                    (
                        sleep 0.26
                        # SINGLE CLICK: Toggle Ambient Listening / Music Detection
                        if [ -f /data/local/tmp/luffy_ambient ]; then
                            rm -f /data/local/tmp/luffy_ambient
                            am broadcast -a com.luffy.core.ACTION_AMBIENT_TOGGLE --ez enabled false >/dev/null 2>&1
                        else
                            touch /data/local/tmp/luffy_ambient
                            am broadcast -a com.luffy.core.ACTION_AMBIENT_TOGGLE --ez enabled true >/dev/null 2>&1
                        fi
                    ) &
                    TIMER_PID=$!
                fi
            fi
            ;;
    esac
done
