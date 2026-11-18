# Motrix for Android

> **Unofficial Android port** of [Motrix](https://github.com/agalwood/Motrix) — a full-featured download manager.
> Native Kotlin UI + the original Motrix web UI ("3B"), powered by a bundled **aria2** engine running on-device.

![platform](https://img.shields.io/badge/platform-Android%207%2B-3ddc84)
![language](https://img.shields.io/badge/language-Kotlin-7f52ff)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)
![license](https://img.shields.io/badge/license-MIT-green)

> ⚠️ This is a community, **non-official** port. It is **not affiliated with or endorsed by** the Motrix project or its authors. "Motrix" is the name of the upstream project; this repository is an independent Android adaptation.

---

## Overview

Motrix for Android brings the Motrix download experience to phones and tablets with **two interchangeable UIs**:

- **Native UI** — a lightweight, touch-first Kotlin interface (fast, low memory).
- **Classic UI (3B)** — the **original Motrix React web UI**, served locally and bridged to the on-device engine, for maximum visual/feature fidelity.

Both UIs drive the **same embedded aria2 engine** (`arm64-v8a` / `armeabi-v7a` static binaries bundled in `jniLibs`), so no external server or PC is required — the app is fully standalone.

## Features

**Downloads**
- HTTP / HTTPS links, `magnet:` links, and `.torrent` files (multi-file selection with per-file checkboxes)
- Pause / resume (single & all), remove, re-add, clear completed
- Task details: file name, size, progress, save path, open file / export to phone / copy path
- Rename finished files
- Download directory picker (SAF) and configurable default location

**Engine & settings**
- Three-level speed limit (Turtle / Normal / High) — default High (unlimited)
- Tracker manager (editable list of public trackers)
- Full download/BitTorrent settings: max connections per server, split count, max BT peers, DHT toggle, listen port range, RPC port/secret
- Live speed chart (last 60 s) on the dashboard

**Experience**
- Notification center (history of completed/failed tasks)
- Light / Dark theme (follow system or force)
- Chinese / English (follow system or force)
- Fully responsive layout tuned for **phone, tablet, landscape and various DPI**
- System status-bar & top-bar safe-area handling
- Signed **Release** build (non-debuggable)
- Open `.torrent` files from any file manager via intent

## Architecture

```
┌───────────────────────────── App process ─────────────────────────────┐
│                                                                        │
│  Native UI (Kotlin)                 Classic UI "3B" (WebView)          │
│  RecyclerView + Material            ├─ BridgeServer (NanoHTTPD/NanoWSD)│
│        │                            │     • serves renderer-web assets  │
│        │                            │     • /rpc/{query|command}/<chan> │
│        │                            │     • /rpc/events (WebSocket)     │
│        │                            └─ RpcDispatcher  (channel→aria2)   │
│        └──────────────┬───────────────────────┘                        │
│                       ▼                                                │
│             Engine (aria2 JSON-RPC client)                             │
│                       ▼                                                │
│        aria2c  (bundled static binary, 127.0.0.1:<rpcPort>)            │
└────────────────────────────────────────────────────────────────────────┘
```

- **Engine** — spawns the bundled `aria2c`, talks JSON-RPC over loopback.
- **Classic UI (3B)** — the real Motrix React renderer (`renderer-web/`) is loaded same-origin from a local `BridgeServer`; because the renderer derives its backend base URL from `location.origin`, it talks to our bridge transparently. `RpcDispatcher` maps Motrix channels to aria2 RPC calls.
- **Native UI** — talks to `Engine` directly.

## Download

A prebuilt, signed release APK is available in this repository:

```
dist/motrix-android-2.4.3-release-signed.apk
```

Requirements: Android 7.0+ (API 24), `arm64-v8a` or `armeabi-v7a`.

> The APK is signed with a project release key (non-debuggable). Installing from outside an app store may show the usual "unknown source" prompt — that is a generic system notice, not a virus warning.

## Build from source

**Requirements**
- JDK 17+ (JDK 21 tested)
- Android SDK with `platforms;android-35` and `build-tools` 34/35
- Gradle 8.7 (or use the wrapper)

**Build (debug)**
```bash
cd android
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

**Build (signed release)**
1. Provide a keystore and configure `signingConfigs.release` in `android/app/build.gradle.kts`
   (supply your own keystore — no keystore is committed to this repo).
2. Then:
```bash
cd android
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

> Note: if the project path contains non-ASCII characters, add `android.overridePathCheck=true` to `gradle.properties`, or build from an ASCII-only path.

## Project structure

```
android/            Android/Kotlin app (Gradle project)
  app/src/main/java/app/motrix/android/
    engine/         aria2 process, RPC client, models, bencode/torrent parser
    ui/             native UI (adapter, dialogs, speed chart)
    web/            BridgeServer + RpcDispatcher (3B backend)
  app/src/main/assets/
    renderer-web/   original Motrix React renderer (built)
    cacert.pem      CA bundle for aria2 HTTPS
  app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libaria2c.so
docs/               requirements, audit, architecture, build & dev guides
reports/            per-milestone delivery reports & screenshots
scripts/            toolchain install / aria2 fetch / build helpers
renderer-overrides/ mobile adaptation CSS/JS injected into 3B
third_party/aria2/  third-party notice (GPLv2)
tests/              validation scripts
```

## Tech stack

Kotlin · AndroidX / Material 3 · NanoHTTPD + NanoWSD · aria2 (static, bundled) · WebView
Classic UI is the upstream Motrix **React 19 + Vite** renderer.

## Roadmap

- [x] Native UI with full download lifecycle
- [x] Classic UI "3B" with local bridge to on-device aria2
- [x] Multi-file torrent selection, tracker manager, full BT settings
- [x] Speed chart, notifications, theme, i18n, responsive layout
- [x] Signed release build
- [ ] More integrations (browser-extension pairing polish, plugin system)
- [ ] Additional ABIs / Play-ready packaging

## Third-party & license

- This project: **MIT** (see [`LICENSE`](LICENSE)).
- The bundled **aria2** binary is licensed under **GPLv2**; its source is available at <https://aria2.github.io/>. See [`third_party/aria2/NOTICE.md`](third_party/aria2/NOTICE.md).
- The Classic UI is the **Motrix** renderer (MIT) — upstream: <https://github.com/agalwood/Motrix>. This repository is an independent, unofficial port and is not affiliated with the upstream project.

## Disclaimer

Provided "as is", without warranty of any kind. Use at your own risk and in compliance with the laws of your jurisdiction and the terms of the sites you download from.
