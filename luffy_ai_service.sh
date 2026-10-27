#!/system/bin/sh
# Luffy AI Daemon Service - Starts on boot via Magisk service.d

sleep 5

# Ensure Termux permissions
export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export LD_LIBRARY_PATH=$PREFIX/lib
export PATH=$PREFIX/bin:$PATH
export TMPDIR=$PREFIX/tmp

# Check if llama-server is running, if not start it
if ! pgrep -f "llama-server" >/dev/null; then
    su u0_a259 -g 10259 -G 3003 -c "export PREFIX=/data/data/com.termux/files/usr; export HOME=/data/data/com.termux/files/home; export LD_LIBRARY_PATH=\$PREFIX/lib; export PATH=\$PREFIX/bin:\$PATH; nohup \$PREFIX/bin/llama-server -m \$HOME/models/qwen2.5-1.5b-instruct-q4_k_m.gguf --host 127.0.0.1 --port 8081 -c 2048 -t 4 --log-disable >/dev/null 2>&1 &"
fi

# Check if luffy_server.py is running, if not start it
sleep 2
if ! pgrep -f "luffy_server.py" >/dev/null; then
    su u0_a259 -g 10259 -G 3003 -c "export PREFIX=/data/data/com.termux/files/usr; export HOME=/data/data/com.termux/files/home; export LD_LIBRARY_PATH=\$PREFIX/lib; export PATH=\$PREFIX/bin:\$PATH; nohup \$PREFIX/bin/python \$HOME/luffy_server.py >/dev/null 2>&1 &"
fi
