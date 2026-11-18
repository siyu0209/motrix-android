# Motrix 安卓移植工程 — 总蓝图（BLUEPRINT，权威口径）

> 目标：把 `agalwood/Motrix`（v2.4.3 motrix-turbo，Electron + React Web + Fastify server + aria2 引擎）**尽全力**移植为可直接安装运行的**安卓手机 App**，最大保真原有下载/任务/BT/限速/统计等核心业务逻辑与界面，并完成移动端适配、美化、bug 修复、性能与安全优化。

## 一、为什么可行（架构判定）
Motrix 是「**React Web 渲染层（dist/renderer-web） ↔ bridge/HTTP ↔ Fastify 服务端（dist/server）+ core（aria2/ffmpeg 引擎）**」的 client-server 结构；服务端可独立运行（`build:server` / `start:server` / docker / NAS）。因此安卓端不需要 Electron，只需要：**一个 WebView 承载 renderer + 一个本机后端服务 + aria2 安卓二进制**。

## 二、双模运行（本工程同时实现，用户可切换）
- **模式 B（默认，端内全内嵌，最忠实）**：App 内用 **nodejs-mobile** 启动真实的 `dist/server`（Node 运行时内嵌），并用**安卓版 `aria2c` 二进制**做下载引擎，全部跑在 `127.0.0.1:<port>`；WebView 加载本地 renderer，向后端取数。整机离线可用。
- **模式 A（远程服务器，兜底/轻量）**：WebView 直接连一台运行 Motrix server 的 PC/NAS（填 URL + bridge 配对），手机当远程遥控客户端。用于内嵌 Node 跑不动或用户已有服务器时。

两种模式共用同一个 WebView 壳与 renderer 资源；差异只在「后端指向 localhost 还是远程 URL」。

## 三、目录契约（谁写哪里，禁止越界）
```
motrix-android/
├─ BLUEPRINT.md                # 本文件（Lead 维护）
├─ README.md                   # Lead
├─ docs/
│   ├─ 00-需求转译.md          # req-translator
│   ├─ 01-源码审计.md          # reverse-auditor
│   ├─ 02-架构方案.md          # Lead
│   ├─ 03-构建指南.md          # perf-sec-builder
│   ├─ 04-差距与路线图.md      # req-translator
│   ├─ 05-交付清单.md          # req-translator
│   └─ 06-QA测试计划.md        # qa-fixer
├─ android/                    # Lead 主导（Gradle 工程）
│   ├─ settings.gradle.kts / build.gradle.kts / gradle.properties
│   ├─ app/build.gradle.kts / app/src/main/AndroidManifest.xml
│   ├─ app/src/main/java/...   # MainActivity / WebView / 服务 / 桥
│   ├─ app/src/main/res/...
│   └─ app/src/main/assets/
│       ├─ renderer-web/       # 由 dist/renderer-web 拷入（构建脚本自动 stage）
│       └─ nodejs-project/     # multiplatform-dev 写：node 启动脚本
├─ scripts/                    # perf-sec-builder
│   ├─ install-toolchain.ps1   # 装 JDK17 + Android SDK + NDK
│   ├─ fetch-aria2-android.ps1 # 取安卓 aria2c（arm64-v8a / armeabi-v7a）
│   ├─ build-renderer.ps1      # pnpm 构建 dist/renderer-web + dist/server
│   └─ build-all.ps1           # 一键：renderer→stage→gradle assemble
├─ renderer-overrides/         # ui-ux-designer（移动端适配 CSS/JS/说明）
├─ third_party/aria2/          # aria2 二进制与 LICENSE/来源说明
├─ reports/                    # 各角色交回的验证/尝试结论
└─ tests/                      # qa-fixer 的校验脚本
```

## 四、技术要点（给各角色对齐）
1. **renderer 构建**：`pnpm install && pnpm build:server` 产出 `dist/renderer-web`（静态站，base './'）与 `dist/server`（Fastify，入口 `dist/server/index.mjs`）。构建脚本把 `dist/renderer-web` 拷进 `android/app/src/main/assets/renderer-web`。
2. **本机后端（模式B）**：nodejs-mobile 加载 `assets/nodejs-project/main.js`，设环境变量（MOTRIX 监听端口、下载目录=应用外部存储、`MOTRIX_SKIP_ELECTRON_REBUILD=1`），`import('.../dist/server/index.mjs')` 起服务。
3. **aria2 引擎**：`src/server/start-engine.ts` 会拉起 aria2c；安卓需把 aria2c 放 `jniLibs/<abi>/libaria2c.so`（可执行），App 启动时赋可执行权限并指定路径（环境变量 MOTRIX_ARIA2_PATH 或等价）。
4. **WebView 桥**：`addJavascriptInterface` 暴露 `MotrixAndroid`：pickDownloadDir(SAF)、notify(通知)、getServerBase()、storageInfo、keepAwake 等；renderer 侧用一小段注入脚本把「桌面 electron API」替换为 Web 桥（Motrix web 模式本就以 HTTP 连 server，多数无需改）。
5. **前台服务**：下载期间 `foregroundService` 保活 + 通知进度；权限：`INTERNET`、`FOREGROUND_SERVICE`、`POST_NOTIFICATIONS`、`WRITE` 走 SAF，最小化。
6. **已知硬骨头（写进差距与路线图，诚实标注）**：`better-sqlite3`（C++ 原生）与 `.cargo`/`packages/native-host`（Rust）需针对 Android NDK 交叉编译；nodejs-mobile 内置 Node 版本可能低于 26，需验证 server 用到的 Node API。构建脚本与文档要给出这些的解法与降级（如 server 侧 sqlite 走纯 JS 兜底 / 远程模式）。

## 五、红线条（全员）
- 功能保真优先；无法在端上还原的点，明确标注并给替代，不隐瞒。
- 产物可复现：所有构建步骤脚本化；依赖最小化、整合进产物。
- 交付含：可运行产物/工程源码/一键构建脚本/全套文档/二次开发指南。
