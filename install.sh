#!/data/data/com.termux/files/usr/bin/bash
set -e
pkg update -y
pkg install -y python git
# Termux manages its own pip; do not try to upgrade pip itself.
python -m pip install -r requirements.txt
mkdir -p workspace
echo
echo "RUK AI Coding Agent installed."
echo "Set API settings, then run: python agent.py"
