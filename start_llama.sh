#!/data/data/com.termux/files/usr/bin/bash
export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export LD_LIBRARY_PATH=$PREFIX/lib
export PATH=$PREFIX/bin:$PATH

pkill -f llama-server 2>/dev/null

exec llama-server \
    -m /data/data/com.termux/files/home/models/qwen2.5-1.5b-instruct-q4_k_m.gguf \
    --host 127.0.0.1 \
    --port 8081 \
    -c 2048 \
    -t 4 \
    --log-disable
