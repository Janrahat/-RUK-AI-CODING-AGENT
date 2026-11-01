# RUK AI

**All-in-one AI assistant for Android** — chat, image and video generation, a code
assistant with a real execution sandbox, PDF question answering, a twelve-tool AI
library, and a FastAPI backend that keeps every provider key off the device.

This repository also ships an independent **Termux terminal agent** (`agent.py`),
covered in [Termux agent](#termux-agent). The two share a name and nothing else:
the terminal agent is a separate, single-file tool for coding on-device.

<p align="left">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="FastAPI" src="https://img.shields.io/badge/FastAPI-0.115-009688?logo=fastapi&logoColor=white">
  <img alt="Python" src="https://img.shields.io/badge/Python-3.9%2B-3776AB?logo=python&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-yellow.svg">
</p>

---

## Screenshots

Ten screens, rendered from the Compose sources in this repo — the strings, cards,
tabs and colours all come from the Kotlin that ships.

<table>
  <tr>
    <td align="center" width="20%">
      <a href="docs/screenshots/01-splash.webp"><img src="docs/screenshots/01-splash.webp" width="150" alt="Splash"></a><br>
      <sub><b>Splash</b></sub>
    </td>
    <td align="center" width="20%">
      <a href="docs/screenshots/02-dashboard.webp"><img src="docs/screenshots/02-dashboard.webp" width="150" alt="Dashboard"></a><br>
      <sub><b>Dashboard</b></sub>
    </td>
    <td align="center" width="20%">
      <a href="docs/screenshots/03-chat.webp"><img src="docs/screenshots/03-chat.webp" width="150" alt="AI Chat"></a><br>
      <sub><b>AI Chat</b></sub>
    </td>
    <td align="center" width="20%">
      <a href="docs/screenshots/04-tools.webp"><img src="docs/screenshots/04-tools.webp" width="150" alt="AI Tools"></a><br>
      <sub><b>AI Tools</b></sub>
    </td>
    <td align="center" width="20%">
      <a href="docs/screenshots/05-image-generator.webp"><img src="docs/screenshots/05-image-generator.webp" width="150" alt="Image Generator"></a><br>
      <sub><b>Image Gen</b></sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <a href="docs/screenshots/06-video-generator.webp"><img src="docs/screenshots/06-video-generator.webp" width="150" alt="Video Generator"></a><br>
      <sub><b>Video Gen</b></sub>
    </td>
    <td align="center">
      <a href="docs/screenshots/07-code-assistant.webp"><img src="docs/screenshots/07-code-assistant.webp" width="150" alt="Code Assistant"></a><br>
      <sub><b>Code Assistant</b></sub>
    </td>
    <td align="center">
      <a href="docs/screenshots/08-pdf-assistant.webp"><img src="docs/screenshots/08-pdf-assistant.webp" width="150" alt="PDF Assistant"></a><br>
      <sub><b>PDF Assistant</b></sub>
    </td>
    <td align="center">
      <a href="docs/screenshots/09-history.webp"><img src="docs/screenshots/09-history.webp" width="150" alt="History"></a><br>
      <sub><b>History</b></sub>
    </td>
    <td align="center">
      <a href="docs/screenshots/10-profile.webp"><img src="docs/screenshots/10-profile.webp" width="150" alt="Profile"></a><br>
      <sub><b>Profile</b></sub>
    </td>
  </tr>
</table>

Each screen maps to one composable: `SplashScreen.kt`, `HomeScreen.kt`,
`ChatScreen.kt`, `ToolsScreen.kt` + `ToolLibrary.kt`, `ImageGeneratorScreen.kt`,
`VideoGeneratorScreen.kt`, `CodeAssistantScreen.kt`, `PdfAssistantScreen.kt`,
`HistoryScreen.kt`, `ProfileScreen.kt`.

**These are design mockups, not device captures.** A Compose project cannot be
screenshotted without an emulator, so each screen was rebuilt as a 412 dp HTML
layout using the exact strings and `RukColors` values from the Kotlin sources, then
rendered to WebP at 2x device scale. See
[`docs/screenshots/gallery.html`](docs/screenshots/gallery.html) for the full
gallery with per-screen notes. Where the reference design and the code disagree,
the code wins — e.g. the dashboard uses a two-column card grid because
`HomeScreen` calls `cards.chunked(2)`, and the profile shows its usage tiles as two
rows of two because that is what `ProfileScreen` composes.

---

## Table of contents

- [Screenshots](#screenshots)
- [What this is](#what-this-is)