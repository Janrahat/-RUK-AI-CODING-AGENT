"use client";
import { FormEvent, useEffect, useRef, useState } from "react";
type Msg = { role: "user" | "assistant"; content: string };
type SpeechRecognitionLike = { lang: string; continuous: boolean; interimResults: boolean; onresult: ((event: any) => void) | null; onerror: (() => void) | null; onend: (() => void) | null; start: () => void; stop: () => void };
declare global { interface Window { SpeechRecognition?: new () => SpeechRecognitionLike; webkitSpeechRecognition?: new () => SpeechRecognitionLike } }
const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";
export default function Home() {
  const [messages, setMessages] = useState<Msg[]>([{ role: "assistant", content: "السلام علیکم! میں RUK AI GLOBAL ہوں۔ آج میں آپ کی کیا مدد کر سکتا ہوں؟" }]);
  const [input, setInput] = useState(""); const [busy, setBusy] = useState(false); const [listening, setListening] = useState(false);
  const endRef = useRef<HTMLDivElement>(null); const recognition = useRef<SpeechRecognitionLike | null>(null);
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages, busy]);
  async function send(e?: FormEvent) {
    e?.preventDefault(); const text = input.trim(); if (!text || busy) return;
    setInput(""); setMessages(m => [...m, { role: "user", content: text }]); setBusy(true);
    try {
      const res = await fetch(`${API}/api/chat`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ message: text }) });
      const data = await res.json(); if (!res.ok) throw new Error(data.detail || "Request failed");
      setMessages(m => [...m, { role: "assistant", content: data.reply }]);
    } catch (err) { setMessages(m => [...m, { role: "assistant", content: `معذرت، رابطہ نہیں ہو سکا: ${err instanceof Error ? err.message : "Unknown error"}` }]); }
    finally { setBusy(false); }
  }
  function startVoice() {
    const Speech = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Speech) { alert("Voice input اس browser میں دستیاب نہیں۔ Chrome استعمال کریں یا text لکھیں۔"); return; }
    if (listening) { recognition.current?.stop(); setListening(false); return; }
    const r = new Speech(); recognition.current = r; r.lang = "ur-PK"; r.continuous = false; r.interimResults = false;
    r.onresult = event => { const heard = event.results?.[0]?.[0]?.transcript; if (heard) setInput(old => old ? `${old} ${heard}` : heard); };
    r.onerror = () => setListening(false); r.onend = () => setListening(false); r.start(); setListening(true);
  }
  function speak(text: string) { if (typeof window !== "undefined" && "speechSynthesis" in window) { window.speechSynthesis.cancel(); const u = new SpeechSynthesisUtterance(text); u.lang = "ur-PK"; window.speechSynthesis.speak(u); } }
  return <main className="shell"><aside className="sidebar"><div className="brand"><div className="logo">R</div><div><b>RUK AI</b><small>GLOBAL</small></div></div><div className="side-label">WORKSPACE</div><button className="nav active">✧　New chat</button><button className="nav" onClick={() => setMessages([{role:"assistant",content:"السلام علیکم! نئی گفتگو شروع کریں۔"}])}>▤　Clear chat</button><div className="side-bottom"><span className="dot"/> System interface ready <small>v0.1.0 · Starter</small></div></aside><section className="main"><header><div><span className="eyebrow">PERSONAL AI ASSISTANT</span><h1>RUK AI GLOBAL</h1></div><div className="status"><span className="dot"/> ONLINE UI</div></header><div className="chat"><div className="welcome"><div className="orb"><span>R</span></div><h2>Your AI. Your World.</h2><p>Ask anything. Build anything. Think without limits.</p><div className="chips"><button onClick={() => setInput("مجھے Python سکھائیں")}>Learn coding</button><button onClick={() => setInput("ایک ویب سائٹ بنانے میں مدد کریں")}>Build a website</button><button onClick={() => setInput("Explain AI in Urdu")}>Explore AI</button></div></div><div className="messages">{messages.map((m,i)=><div className={`message ${m.role}`} key={i}><div className="avatar">{m.role === "user" ? "U" : "R"}</div><div className="bubble"><span className="role">{m.role === "user" ? "YOU" : "RUK AI"}</span><p>{m.content}</p>{m.role === "assistant" && <button className="speak" onClick={() => speak(m.content)}>🔊 Listen</button>}</div></div>)}{busy && <div className="typing">RUK AI is thinking<span> ···</span></div>}<div ref={endRef}/></div></div><form className="composer" onSubmit={send}><button type="button" className={`mic ${listening ? "recording" : ""}`} onClick={startVoice} title="Voice input">{listening ? "■" : "🎙"}</button><input value={input} onChange={e=>setInput(e.target.value)} placeholder="Message RUK AI GLOBAL…" aria-label="Message"/><button className="send" disabled={busy || !input.trim()} type="submit">➤</button><div className="hint">AI can make mistakes. Verify important information.</div></form></section></main>;
}
