# RUK AI Coding Agent — Termux Edition

A lightweight, terminal-based AI coding assistant for Android/Termux. It uses an OpenAI-compatible Chat Completions API with tool calling.

## Features
- Inspect, read, and write project files inside `workspace/`.
- Request development commands such as Python, Git, Node/npm, and pytest.
- Ask before every file write and every command execution.
- Configurable API endpoint and model.
- Conversation reset with `/clear`; exit with `/exit`.

## Install on Android
```bash
pkg update -y
pkg install -y git python
git clone https://github.com/Janrahat/-RUK-AI-CODING-AGENT.git
cd -RUK-AI-CODING-AGENT
bash install.sh
```

## Configure
Set an API key from a provider that supports OpenAI-compatible Chat Completions and tool calling:
```bash
export RUK_API_KEY="YOUR_API_KEY"
export RUK_BASE_URL="https://api.openai.com/v1"
export RUK_MODEL="gpt-4.1-mini"
```
Use the exact base URL and model name supplied by your provider. Never paste your key into source code or upload it to GitHub.

## Run
```bash
python agent.py
```

By default, files are limited to the local `workspace/` directory. To use another directory:
```bash
export RUK_WORKSPACE="$HOME/my-project"
python agent.py
```

## Safety
This is a starter agent, not an unrestricted autonomous computer operator. It blocks shell chaining, pipes, redirection, substitutions, and commands outside a small allowlist. It asks before writing files or running commands. Review generated code before executing it. Do not use it with untrusted repositories without inspection.

## Troubleshooting
- `401` / invalid API key: check `RUK_API_KEY`.
- `404` model or endpoint: check provider's base URL and model identifier.
- Tool calls unsupported: choose a model/provider that supports tool calling.
- API quota/billing error: check the provider's account limits; installing this project does not include free API credits.
- `ModuleNotFoundError`: run `python -m pip install -r requirements.txt`.
