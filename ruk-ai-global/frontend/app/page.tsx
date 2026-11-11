"use client";
import { FormEvent, useEffect, useRef, useState } from "react";
type Msg = { role: "user" | "assistant"; content: string };
type SpeechRecognitionLike = { lang: string; continuous: boolean; interimResults: boolean; onresult: ((event: any) => void) | null; onerror: (() => void) | null; onend: (() => void) | null; start: () => void; stop: () => void };
declare global { interface Window { SpeechRecognition?: new () => SpeechRecognitionLike; webkitSpeechRecognition?: new () => SpeechRecognitionLike } }
const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";
export default function Home() {
  const [messages, setMessages] = useState<Msg[]>([{ role: "assistant", content: "السلام علیکم! میں RUK AI GLOBAL ہوں۔ آج میں آپ کی کیا مدد کر سکتا ہوں؟" }]);
  const [input, setInput] = useState(""); const [busy, setBusy] = useState(false); const [listening, setListening] = useState(false);
  const [apiStatus, setApiStatus] = useState<"checking" | "online" | "offline">("checking");
  const endRef = useRef<HTMLDivElement>(null); const recognition = useRef<SpeechRecognitionLike | null>(null);
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages, busy]);
  useEffect(() => {
    let active = true;
    fetch(`${API}/api/health`).then(r => { if (!r.ok) throw new Error("API unavailable"); return r.json(); })
      .then(() => { if (active) setApiStatus("online"); })
      .catch(() => { if (active) setApiStatus("offline"); });
    return () => { active = false; };
  }, []);
  async function send(e?: FormEvent) {
    e?.preventDefault(); const text = input.trim(); if (!text || busy) return;
    const history = messages.slice(-40);
    setInput(""); setMessages(m => [...m, { role: "user", content: text }]); setBusy(true);
    try {
      const res = await fetch(`${API}/api/chat`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ message: text, history }) });
      const data = await res.json(); if (!res.ok) throw new Error(data.detail || "Request failed");
      setMessages(m => [...m, { role: "assistant", content: data.reply }]); setApiStatus("online");
    } catch (err) {
      const reason = err instanceof Error ? err.message : "Unknown error";
      setMessages(m => [...m, { role: "assistant", content: `معذرت، درخواست مکمل نہیں ہوئی: ${reason}` }]);
      if (reason.toLowerCase().includes("fetch")) setApiStatus("offline");
    } finally { setBusy(false); }
  }
  function newChat() { setMessages([{ role: "assistant", content: "نئی گفتگو شروع ہوگئی۔ میں آپ کی کیا مدد کر سکتا ہوں؟" }]); setInput(""); }
  function startVoice() {
    const Speech = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Speech) { alert("Voice input اس browser میں دستیاب نہیں۔ Chrome استعمال کریں یا text لکھیں۔"); return; }
    if (listening) { recognition.current?.stop(); setListening(false); return; }
    const r = new Speech(); recognition.current = r; r.lang = "ur-PK"; r.continuous = false; r.interimResults = false;
    r.onresult = event => { const heard = event.results?.[0]?.[0]?.transcript; if (heard) setInput(old => old ? `${old} ${heard}` : heard); };
    r.onerror = () => setListening(false); r.onend = () => setListening(false); r.start(); setListening(true);
  }
  function speak(text: string) { if ("speechSynthesis" in window) { window.speechSynthesis.cancel(); const u = new SpeechSynthesisUtterance(text); u.lang = "ur-PK"; window.speechSynthesis.speak(u); } }
  const statusText = apiStatus === "checking" ? "CHECKING API" : apiStatus === "online" ? "API CONNECTED" : "API OFFLINE";
  return <main className="shell">
    <aside className="sidebar"><div className="brand"><div className="logo">R</div><div><b>RUK AI</b><small>GLOBAL</small></div></div>
      <div className="side-label">WORKSPACE</div><button className="nav active" onClick={newChat}>✧　New chat</button><button className="nav" onClick={newChat}>▤　Clear chat</button>
      <div className="side-bottom"><span className={apiStatus === "online" ? "dot" : "dot muted"}/> {statusText}<small>Dashboard v0.2.0</small></div>
    </aside>
    <section className="main"><header><div><span className="eyebrow">PERSONAL AI ASSISTANT</span><h1>RUK AI GLOBAL</h1></div><div className={`status ${apiStatus}`}><span className={apiStatus === "online" ? "dot" : "dot muted"}/> {statusText}</div></header>
      <div className="chat"><div className="welcome"><div className="orb"><span>R</span></div><h2>Your AI. Your World.</h2><p>Ask anything. Build anything. Think without limits.</p>
        <div className="chips"><button onClick={() => setInput("مجھے Python سکھائیں")}>Learn coding</button><button onClick={() => setInput("ایک ویب سائٹ بنانے میں مدد کریں")}>Build a website</button><button onClick={() => setInput("Explain AI in Urdu")}>Explore AI</button></div>
      </div><div className="messages">{messages.map((m,i)=><div className={`message ${m.role}`} key={i}><div className="avatar">{m.role === "user" ? "U" : "R"}</div><div className="bubble"><span className="role">{m.role === "user" ? "YOU" : "RUK AI"}</span><p>{m.content}</p>{m.role === "assistant" && <button className="speak" onClick={() => speak(m.content)}>🔊 Listen</button>}</div></div>)}{busy && <div className="typing">RUK AI is thinking<span> ···</span></div>}<div ref={endRef}/></div></div>
      <form className="composer" onSubmit={send}><button type="button" className={`mic ${listening ? "recording" : ""}`} onClick={startVoice} title="Voice input">{listening ? "■" : "🎙"}</button><input value={input} onChange={e=>setInput(e.target.value)} placeholder="Message RUK AI GLOBAL…" aria-label="Message"/><button className="send" disabled={busy || !input.trim()} type="submit">➤</button><div className="hint">AI can make mistakes. Verify important information.</div></form>
    </section></main>;
}
