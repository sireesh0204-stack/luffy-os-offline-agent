#!/system/bin/sh
# Luffy OS Kernel Key Event Daemon
# Active Hold Trigger & Double/Single Click State-Machine (Strict REG-04, REG-05, REG-06)

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
                # REG-06: Long Hold Bixby - Wake Voice Assistant WHILE user is holding button!
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

                    # REG-04: Double Click Bixby - Toggle E-Ink Monochrome <-> Color
                    CURR=$(settings get secure accessibility_display_daltonizer_enabled)
                    if [ "$CURR" = "1" ]; then
                        settings put secure accessibility_display_daltonizer_enabled 0
                    else
                        settings put secure accessibility_display_daltonizer 0
                        settings put secure accessibility_display_daltonizer_enabled 1
                    fi
                else
                    # First tap -> Start 260ms single-click timer
                    (
                        sleep 0.26
                        # REG-05: Single Click Bixby - Silent Screen Vision Analysis (Screenshot -> OCR -> Qwen Summary)
                        am start -n com.luffy.core/.LuffyMainActivity --es trigger "single_click" --activity-clear-top >/dev/null 2>&1
                    ) &
                    TIMER_PID=$!
                fi
            fi
            ;;
    esac
done
