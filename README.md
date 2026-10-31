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
cd -- -RUK-AI-CODING-AGENT
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


## Android app (new)

A native Kotlin + Jetpack Compose Android client is available in the `app/` folder.
It currently provides a chat interface for OpenAI-compatible providers (including OpenRouter).

### Build APK with GitHub Actions
1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Choose **Run workflow** and run it on `main`.
4. When the run succeeds, open it and download the `ruk-ai-debug-apk` artifact.
5. Extract the artifact ZIP to get `app-debug.apk`, then install it on your Android phone.

### Important
- This Android app is an initial native chat client, not yet a full port of the Python terminal agent's local file tools, command runner, or GitHub automation.
- Enter your provider's API key in the app at runtime. The key is kept in memory only and is not saved to disk by this starter version.
- Use a valid provider base URL and model, e.g. OpenRouter base URL `https://openrouter.ai/api/v1`. Provider usage may incur charges.
- Never commit API keys or paste them into screenshots.
