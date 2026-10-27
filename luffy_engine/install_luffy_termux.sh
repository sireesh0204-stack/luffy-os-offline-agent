#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# Luffy Engine: Termux Setup & Local LLM Deployment Script (Galaxy Note 9)
# ==============================================================================

set -e

echo "[1/5] Updating Termux packages..."
pkg update -y && pkg upgrade -y
pkg install -y python git clang cmake ninja libcurl

echo "[2/5] Setting up Python environment..."
pip install --upgrade pip

echo "[3/5] Building lightweight llama.cpp for aarch64..."
if [ ! -d "llama.cpp" ]; then
    git clone --depth 1 https://github.com/ggerganov/llama.cpp
    cd llama.cpp
    cmake -B build -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF
    cmake --build build --config Release -j 4 --target llama-server
    cd ..
fi

echo "[4/5] Downloading Qwen2.5-1.5B-Instruct quantized GGUF..."
mkdir -p models
if [ ! -f "models/qwen2.5-1.5b-instruct-q4_k_m.gguf" ]; then
    curl -L -o models/qwen2.5-1.5b-instruct-q4_k_m.gguf \
        https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf
fi

echo "[5/5] Launching Luffy Local Agent Daemon..."
python3 luffy_server.py &
echo "SUCCESS: Luffy is running 100% locally and offline on port 8080!"
