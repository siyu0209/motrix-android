# Motrix for Android（安卓移植工程）

把官方 **agalwood/Motrix v2.4.3（motrix-turbo）** 下载管理器移植到 Android 手机的宿主工程。

Motrix 上游已是「React Web UI ↔ HTTP/WebSocket ↔ Fastify 后端 + aria2/ffmpeg 引擎」的 **client-server** 架构（原生支持 web/server 目标与远程服务器部署），本工程据此设计 **两种运行模式**：

| 模式 | 说明 | 原生依赖 | 适用 |
|---|---|---|---|
| **A. remote（远程服务器）** | App 内 WebView 加载**官方 Motrix Web UI**，连一个运行中的 Motrix server（PC/NAS/Docker/本机）。功能=官方服务端具备的全部能力。 | 零原生依赖，开箱可编 | 首选、稳定、保真 |
| **B. embedded（端内内嵌，实验）** | App 内用 nodejs-mobile 跑 `dist/server`，WebView 连 `127.0.0.1` 本地后端，配 aria2 安卓二进制。 | 需 nodejs-mobile + aria2c 交叉编译 | 追求单机离线 |

> 移动端适配（安全区/触控/单列/暗色/返回键接管）由 `../renderer-overrides/mobile-adapt.css|js` 提供，构建时打入 `app/src/main/assets/`，App 在页面加载后自动注入。

## 目录
```
android/
├─ settings.gradle.kts / build.gradle.kts / gradle.properties
├─ gradle/wrapper/…              # Gradle Wrapper（gradlew 由 `gradle wrapper` 生成）
├─ app/
│  ├─ build.gradle.kts           # remote / embedded 两个 flavor
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/app/motrix/android/  # MotrixApp, MainActivity, MotrixJsBridge, MotrixServerService, Prefs
│     ├─ assets/                  # mobile-adapt.css|js + nodejs-project/（模式B 后端引导）
│     └─ res/…                    # 布局/主题/图标/网络安全配置
└─ build-apk.ps1                  # 一键构建脚本
```

## 环境要求
- **JDK 17+**（本机用 JDK21 验证通过）
- **Android SDK**：`platforms;android-35`、`build-tools;35.0.0`、`platform-tools`
- 已接受 SDK 许可（`licenses/android-sdk-license`）
- 联网（首次构建拉取 AGP 8.6.1 / Kotlin 1.9.24 / AndroidX / Material）

## 构建
```powershell
# 方式1：直接用 Gradle（本机已备 gradle-8.7 于 ..\..\tools）
cd android
$env:JAVA_HOME="C:\Users\zhoutao\AppData\Local\Programs\rea-deps\jdk-21.0.13+9"
$env:ANDROID_HOME="C:\Users\zhoutao\AppData\Local\Android\Sdk"
gradle assembleRemoteDebug

# 方式2：wrapper（首次先 gradle wrapper 生成 gradlew）
.\gradlew assembleRemoteDebug
```
产物：`app/build/outputs/apk/remote/debug/app-remote-debug.apk`
安装：`adb install -r app-remote-debug.apk`

## 模式A 用法（推荐）
1. 先跑一个 Motrix server（任选其一）：
   - 官方 docker：`docker compose up`（见上游 `compose.yaml`）
   - 源码：`pnpm install && pnpm build:server && MOTRIX_SKIP_ELECTRON_REBUILD=1 node dist/server/index.mjs`
2. 手机与服务器同网；App 首启在配置面板填 `http://<服务器IP>:<端口>`，点「连接」。
3. 之后即为官方 Motrix 完整 UI（下载/BT/磁力/限速/统计…）。

## 模式B 用法（端内内嵌，实验）
需额外准备（详见 `../docs/02-架构与移植方案.md` 与 `../docs/03-构建指南.md`）：
1. `../scripts/build-renderer.ps1` 把 `dist/renderer-web`→assets/renderer-web、`dist/server`→assets/nodejs-project/dist-server
2. `../scripts/fetch-aria2-android.ps1` 备好 `aria2c` 安卓二进制 → `jniLibs/<abi>/`
3. 放入 nodejs-mobile `aar` 并取消 `app/build.gradle.kts` 里 `embeddedImplementation` 注释
4. `gradle assembleEmbeddedDebug`
> better-sqlite3 / Rust native-host 的 NDK 交叉编译是头号硬骨头；不可用时可走内存驱动或退回模式A。

## 已知说明
- 远程模式下 `94%~100%` 功能与桌面一致（下载、BT、磁力、限速、RPC、插件等取决于服务端）。
- 移动端布局细节需按实际 DOM 微调（见 `../renderer-overrides/screens.md`）。
