# Motrix for Android · Motrix 安卓移植

**简体中文** ｜ [English](#english)

> **非官方** Android 移植版 · Unofficial Android port of [Motrix](https://github.com/agalwood/Motrix) —— 全功能下载管理器。
> 原生 Kotlin 界面 + 原版 Motrix Web 界面（"3B"），内置 **aria2** 引擎，端内独立运行。

![platform](https://img.shields.io/badge/platform-Android%207%2B-3ddc84)
![language](https://img.shields.io/badge/language-Kotlin-7f52ff)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)
![license](https://img.shields.io/badge/license-MIT-green)

---

## 中文

### 简介
Motrix for Android 把 Motrix 的下载体验带到手机/平板，提供**两套可切换界面**：
- **原生界面**：轻量、触屏优先的 Kotlin 界面（快、省内存）。
- **原版界面（3B）**：直接加载**原版 Motrix React Web UI**，本地服务 + 桥接驱动端内引擎，最大程度还原原版观感与功能。

两套界面共用**同一个端内 aria2 引擎**（`arm64-v8a` / `armeabi-v7a` 静态二进制，随包内置），无需电脑或外部服务，App 完全独立运行。

> ⚠️ 本项目为社区**非官方**移植，与 Motrix 上游项目及作者**无隶属、无背书**。

### 功能特性
- 下载：HTTP/HTTPS 直链、`magnet:` 磁力、`.torrent` 种子（多文件勾选）；暂停/恢复（单个与全部）、删除、重新下载、清除已完成；任务详情（打开文件 / 导出到手机 / 复制路径）、完成后重命名、下载目录选择（SAF）。
- 引擎与设置：三档限速（龟速/常速/高速，默认高速不限速）、Tracker 管理、完整下载/BT 设置（单服务器连接数、分片数、BT 最大连接数、DHT、监听端口、RPC 端口/密钥）。
- 体验：首页速度趋势图（近 60 秒）、通知中心、浅色/深色主题（跟随系统或强制）、中文/英文（跟随系统或强制）、手机/平板/横屏全环境自适应、状态栏与顶栏安全区适配、`.torrent` 外部打开、**正式签名 Release 包（非 debuggable）**。

### 下载
仓库内提供已签名 Release 包：
```
dist/motrix-android-2.4.3-release-signed.apk
```
要求：Android 7.0+（API 24），`arm64-v8a` 或 `armeabi-v7a`。
> 包为正式签名、非可调试。非应用商店安装时可能出现"未知来源"提示，这是系统通用提示，非病毒告警。

### 从源码构建
**环境**：JDK 17+（实测 JDK 21）；Android SDK 含 `platforms;android-35`、`build-tools` 34/35；Gradle 8.7（或 wrapper）。
```bash
cd android
./gradlew assembleDebug      # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # 需在 build.gradle.kts 配好 signingConfigs.release
```
> 若工程路径含非 ASCII 字符，请给 `gradle.properties` 加 `android.overridePathCheck=true`，或放到纯 ASCII 路径构建。

### 项目结构
```
android/            安卓 Kotlin 工程
  app/src/main/java/app/motrix/android/
    engine/         aria2 进程、RPC 客户端、模型、bencode/种子解析
    ui/             原生界面（适配器、对话框、速度图表）
    web/            BridgeServer + RpcDispatcher（3B 后端）
  app/src/main/assets/renderer-web/   原版 Motrix React 渲染器（已构建）
  app/src/main/assets/cacert.pem      aria2 HTTPS 证书
  app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libaria2c.so
docs/               需求、审计、架构、构建与二次开发文档
reports/            各里程碑交付报告与截图
scripts/            工具链安装 / aria2 获取 / 构建脚本
renderer-overrides/ 注入 3B 的移动端适配 CSS/JS
third_party/aria2/  第三方声明（GPLv2）
tests/              校验脚本
```

### 许可证与第三方
- 本项目：**MIT**（见 [`LICENSE`](LICENSE)）。
- 内置 **aria2** 二进制为 **GPLv2**，源码见 <https://aria2.github.io/>，详见 [`third_party/aria2/NOTICE.md`](third_party/aria2/NOTICE.md)。
- 原版界面来自 **Motrix** 渲染器（MIT），上游 <https://github.com/agalwood/Motrix>。本项目为独立非官方移植，与上游无隶属关系。

### 免责声明
按"现状"提供，不附带任何担保。请自行承担使用风险，并遵守所在地区法律及所访问站点的条款。

---

<a id="english"></a>
## English

### Overview
Motrix for Android brings the Motrix download experience to phones and tablets with **two interchangeable UIs**:
- **Native UI** — a lightweight, touch-first Kotlin interface (fast, low memory).
- **Classic UI (3B)** — the **original Motrix React web UI**, served locally and bridged to the on-device engine for maximum fidelity.

Both drive the **same embedded aria2 engine** (`arm64-v8a` / `armeabi-v7a` static binaries in `jniLibs`) — no PC or external server required; the app is fully standalone.

> ⚠️ Community, **non-official** port. Not affiliated with or endorsed by the upstream Motrix project.

### Features
- Downloads: HTTP/HTTPS links, `magnet:` links, `.torrent` (multi-file selection); pause/resume (single & all), remove, re-add, clear completed; task details (open / export to phone / copy path), rename finished files, SAF directory picker.
- Engine & settings: 3-level speed limit (Turtle/Normal/High, default unlimited), tracker manager, full download/BT settings (max connections per server, split, max BT peers, DHT, listen port, RPC port/secret).
- Experience: 60-second speed chart, notification center, light/dark theme (system or forced), Chinese/English (system or forced), responsive for phone/tablet/landscape, status-bar & top-bar safe-area handling, open `.torrent` via intent, signed **Release** build (non-debuggable).

### Download
Prebuilt signed APK in this repo:
```
dist/motrix-android-2.4.3-release-signed.apk
```
Requires Android 7.0+ (API 24), `arm64-v8a` or `armeabi-v7a`.

### Build from source
Requirements: JDK 17+ (JDK 21 tested), Android SDK with `platforms;android-35` + `build-tools` 34/35, Gradle 8.7 (or wrapper).
```bash
cd android
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # configure signingConfigs.release first
```

### License & third-party
- This project: **MIT** (see [`LICENSE`](LICENSE)).
- Bundled **aria2** binary is **GPLv2** (<https://aria2.github.io/>), see [`third_party/aria2/NOTICE.md`](third_party/aria2/NOTICE.md).
- Classic UI is the **Motrix** renderer (MIT), upstream <https://github.com/agalwood/Motrix>. Independent, unofficial port.

### Disclaimer
Provided "as is", without warranty of any kind. Use at your own risk and comply with local laws and the terms of the sites you download from.
