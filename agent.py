#!/usr/bin/env python3
"""RUK AI Coding Agent — a permission-first, OpenAI-compatible coding assistant."""
import os, json, subprocess, shlex
from pathlib import Path
import requests
from rich.console import Console
from rich.prompt import Prompt, Confirm
from rich.panel import Panel
from rich.markdown import Markdown

console = Console()
ROOT = Path(os.environ.get("RUK_WORKSPACE", "./workspace")).expanduser().resolve()
ROOT.mkdir(parents=True, exist_ok=True)
API_KEY = os.environ.get("RUK_API_KEY", "")
BASE_URL = os.environ.get("RUK_BASE_URL", "https://api.openai.com/v1").rstrip("/")
MODEL = os.environ.get("RUK_MODEL", "gpt-4.1-mini")

SYSTEM = f"""You are RUK AI Coding Agent, a careful software engineering assistant.
Workspace: {ROOT}
You may inspect and edit files only inside this workspace.
Use tools for file operations. Before running a command, explain why and ask the user.
Never ask for passwords, tokens, or private keys. Never expose secrets.
Do not create malware, credential stealers, phishing pages, covert tracking, or destructive code.
Prefer small changes, explain them, and run relevant tests only after user approval.
"""

TOOLS = [
 {"type":"function","function":{"name":"list_files","description":"List files and folders in workspace (relative path).","parameters":{"type":"object","properties":{"path":{"type":"string","default":"."}},"required":[]}}},
 {"type":"function","function":{"name":"read_file","description":"Read a UTF-8 text file inside workspace.","parameters":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}}},
 {"type":"function","function":{"name":"write_file","description":"Create or replace a UTF-8 text file inside workspace. Show the user the path and ask confirmation before writing.","parameters":{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}}},
 {"type":"function","function":{"name":"run_command","description":"Run a command in workspace. Only safe development commands are allowed, and user approval is required.","parameters":{"type":"object","properties":{"command":{"type":"string"}},"required":["command"]}}}
]
ALLOWED = {"python","python3","pip","pip3","git","node","npm","pytest","ls","pwd","cat","mkdir","grep","find"}

def safe_path(rel):
    p = (ROOT / rel).resolve()
    if p != ROOT and ROOT not in p.parents:
        raise ValueError("Path is outside the workspace.")
    return p

def execute_tool(name, args):
    try:
        if name == "list_files":
            p = safe_path(args.get("path", "."))
            if not p.exists(): return "Path does not exist."
            return "\\n".join(str(x.relative_to(ROOT)) + ("/" if x.is_dir() else "") for x in sorted(p.iterdir()))
        if name == "read_file":
            p = safe_path(args["path"])
            if not p.is_file(): return "File not found."
            if p.stat().st_size > 200_000: return "File too large (limit 200 KB)."
            return p.read_text(encoding="utf-8")
        if name == "write_file":
            p = safe_path(args["path"])
            if not Confirm.ask(f"Allow writing {p.relative_to(ROOT)}?"):
                return "User denied file write."
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(args["content"], encoding="utf-8")
            return f"Saved {p.relative_to(ROOT)} ({p.stat().st_size} bytes)."
        if name == "run_command":
            cmd = args["command"]
            parts = shlex.split(cmd)
            if not parts or parts[0] not in ALLOWED:
                return "Blocked: command is not on the safe allowlist. Allowed: " + ", ".join(sorted(ALLOWED))
            if any(x in cmd for x in (";", "&&", "||", "|", ">", "<", "`", "$(", "\\n")):
                return "Blocked: shell chaining, redirection and substitutions are disabled."
            console.print(Panel(f"[bold yellow]Requested command:[/bold yellow]\\n{cmd}\\n\\nWorking directory: {ROOT}"))
            if not Confirm.ask("Run this command?"):
                return "User denied command."
            result = subprocess.run(parts, cwd=ROOT, text=True, capture_output=True, timeout=120)
            return f"exit_code={result.returncode}\\nSTDOUT:\\n{result.stdout[-12000:]}\\nSTDERR:\\n{result.stderr[-6000:]}"
        return "Unknown tool."
    except Exception as e:
        return f"Tool error: {type(e).__name__}: {e}"

def ask(messages):
    response = requests.post(
        BASE_URL + "/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type":"application/json"},
        json={"model":MODEL, "messages":messages, "tools":TOOLS, "tool_choice":"auto", "temperature":0.2},
        timeout=90
    )
    if not response.ok:
        raise RuntimeError(f"API {response.status_code}: {response.text[:1500]}")
    return response.json()["choices"][0]["message"]

def main():
    console.print(Panel.fit("[bold gold1]RUK AI CODING AGENT[/bold gold1]\\n[cyan]Android / Termux Edition[/cyan]\\nPermission-first coding assistant", border_style="yellow"))
    if not API_KEY:
        console.print("[red]RUK_API_KEY is not set.[/red]\\nSet it with: export RUK_API_KEY='your-api-key'")
        return
    console.print(f"Workspace: [green]{ROOT}[/green]\\nModel: [cyan]{MODEL}[/cyan]\\nType /exit to quit, /clear to reset.")
    messages = [{"role":"system","content":SYSTEM}]
    while True:
        try:
            prompt = Prompt.ask("\\n[bold green]RUK[/bold green]")
            if prompt.strip() == "/exit": break
            if prompt.strip() == "/clear":
                messages = [{"role":"system","content":SYSTEM}]
                console.print("[yellow]Conversation cleared.[/yellow]")
                continue
            if not prompt.strip(): continue
            messages.append({"role":"user","content":prompt})
            for _ in range(8):
                msg = ask(messages)
                messages.append(msg)
                if msg.get("tool_calls"):
                    for call in msg["tool_calls"]:
                        name = call["function"]["name"]
                        args = json.loads(call["function"]["arguments"] or "{}")
                        console.print(f"[dim]Tool: {name}[/dim]")
                        result = execute_tool(name, args)
                        messages.append({"role":"tool","tool_call_id":call["id"],"content":str(result)})
                    continue
                if msg.get("content"):
                    console.print(Panel(Markdown(msg["content"]), border_style="cyan", title="RUK AI"))
                break
            else:
                console.print("[yellow]Stopped after 8 tool steps for safety.[/yellow]")
        except KeyboardInterrupt:
            console.print("\\n[yellow]Interrupted.[/yellow]")
        except Exception as e:
            console.print(f"[red]Error:[/red] {e}")

if __name__ == "__main__":
    main()
