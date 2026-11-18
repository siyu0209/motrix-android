# nodejs-project — Motrix 安卓「模式 B 端内嵌后端」引导层

本目录是 **nodejs-mobile 的 Node 运行时入口工程**：App 启动时由 nodejs-mobile 执行
`main.js`，在其中拉起 Motrix 真实后端 `dist/server`（Fastify，监听 `127.0.0.1:<port>`），
配合安卓版 `aria2c` 作为下载引擎，实现「整机离线可用」的模式 B。

> 本目录**只放 Node 侧脚本与最小 `package.json`**，不参与 Gradle 编译。Android 原生壳
> （MainActivity / WebView / 前台服务 / 桥）由 Lead 负责，不在本目录。

---

## 一、目录放置结构

构建完成后，本目录在 APK 内的最终布局应如下（nodejs-mobile 首次运行时会把
`assets/nodejs-project/` 整套解到 App 的 `files/` 目录再执行）：

```
android/app/src/main/assets/nodejs-project/
├─ main.js            # 入口：读 env → 设 cwd/tmp → import dist-server → 就绪握手 → 优雅退出
├─ package.json       # {"type":"module","main":"main.js"}
├─ healthcheck.js     # 可选：探测端口就绪
├─ README.md          # 本文件
└─ dist-server/       # ★ 由构建脚本从 Motrix 工程的 dist/server 拷入（本仓不手写）
   └─ index.mjs       # Fastify 后端入口（ESM）
```

- **`dist-server/` 由构建脚本 stage**：`scripts/build-renderer.ps1`（perf-sec-builder 维护）
  在 `pnpm build:server` 产出 `dist/server/` 后，把它整体复制到
  `android/app/src/main/assets/nodejs-project/dist-server/`。
  **不要手工往 `dist-server/` 里写代码**，它是构建产物，重新构建会被覆盖。
- 若 `dist-server/index.mjs` 缺失，`main.js` 会打印明确报错并写失败握手
  `motrix-ready.json`（`ready:false`），此时应检查构建/拷贝步骤。

---

## 二、环境变量契约（全部由 Android 原生侧注入，`main.js` 读取后规范化写回）

| 变量 | 是否必需 | 说明 |
|---|---|---|
| `MOTRIX_PORT` | 建议 | 期望监听端口，默认 `16800`。被占用时会自动向上探测，真实端口见握手文件/日志。 |
| `MOTRIX_HOST` | 否 | 监听地址，默认 `127.0.0.1`（仅本机，安全最小暴露）。 |
| `MOTRIX_DOWNLOAD_DIR` | 建议 | 下载目录，指向**应用外部存储**（SAF 授权后的真实路径）。不可写时退回 `<DATA_DIR>/downloads` 并告警。 |
| `MOTRIX_DATA_DIR` | 建议 | 应用私有可写目录（server 配置 / sqlite / 日志 / ready 文件都放这）。缺省回退 HOME 或系统临时目录。 |
| `MOTRIX_SKIP_ELECTRON_REBUILD` | 由 main.js 兜底为 `1` | 跳过 Electron 相关重建逻辑（端上无 Electron）。 |
| `MOTRIX_ARIA2_PATH` | 需要 aria2 时 | 安卓 `aria2c` 可执行文件路径（`jniLibs/<abi>/libaria2c.so`），透传给上游 start-engine。 |
| `MOTRIX_PORT_SCAN_LIMIT` | 否 | 端口扫描上限，默认 50。 |
| `MOTRIX_START_ATTEMPTS` | 否 | 启动重试次数，默认 5。 |
| `MOTRIX_READY_TIMEOUT_MS` | 否 | 等 HTTP 就绪超时，默认 30000。 |
| `MOTRIX_SQLITE_DRIVER` | 降级用 | 设 `memory` 走纯 JS/内存库（better-sqlite3 加载失败时）。 |
| `MOTRIX_REMOTE` | 降级用 | 设 `1` 切远程模式 A（不在端内起后端，仅记录提示）。 |

---

## 三、就绪握手（供 Android 侧判断 ready + 取真实端口）

`main.js` 在服务真正监听后，会同时给出三种信号，原生侧任选其一：

1. **日志行（推荐 logcat grep）**：`[MOTRIX_READY] {"port":16800,"host":"127.0.0.1",...}`
   失败时打 `[MOTRIX_FAILED] <原因>`。
2. **握手文件**：`<MOTRIX_DATA_DIR>/motrix-ready.json`，内容含 `ready/port/host/pid/dataDir/downloadDir/nodeVersion`。
3. **JS 全局**：原生侧通过 nodejs-mobile 求值 `globalThis.motrixAndroid.getStatus()` 得到同样信息；
   需要停服时调用 `globalThis.motrixAndroid.shutdown('reason')`。

> 端口可能因占用而**不是**期望值，WebView 的 `getServerBase()` 必须以握手里的真实
> `port` 为准，不要写死。

健康检查端口：即上述真实 `port`，默认探测路径 `/`（任何 HTTP 状态码视为已监听）。
可用 `healthcheck.js` 验证：`MOTRIX_PORT=<port> node healthcheck.js`（0=就绪 / 1=连接失败 / 2=超时）。

---

## 四、nodejs-mobile 内置 Node 版本风险（务必在真机验证）

`nodejs-mobile` 官方发行版内置的 Node 版本通常**明显落后**于 Motrix 构建所用的 Node
（Motrix 现代版本开发/构建常在 Node 18/20/22 上跑，甚至比 `engines` 更高）。风险点：

- **Node API 缺失**：`dist-server` 若用到较新的 Node 内置 API（如 `structuredClone`、
  `node:test`、较新的 `fs`/`node:sqlite`/`fetch` 全局、`AbortSignal.timeout` 等），在旧运行时上
  可能 `undefined`。→ 需核对 `dist/server` 里实际用到的 API；必要时用 `core-js` 或手写 polyfill 垫片
  （`main.js` 已有 TMPDIR/HOME/XDG 等文件系统垫片，但**语言/内置 API 级**垫片仍要按实际报错补）。
- **原生模块 ABI（NODE_MODULE_VERSION）**：`better-sqlite3` 这类 C++ addon 必须针对**内置 Node 的
  确切 ABI 版本**交叉编译；用其它 Node 版本编的 `.node` 会报 `NODE_MODULE_VERSION`/`invalid ELF`。
- **ESM 支持**：需内置 Node ≥ 12（带 `--experimental-modules` 的旧版不行），建议内置 Node ≥ 16
  以稳妥支持顶层 `await import()`、`node:` 前缀。据此 `package.json` 的 `engines` 标注为 `>=16`。
- **`process.cwd()` / `os.tmpdir()`**：Android 上默认可能是 `/`、`/tmp` 不可写 —— `main.js` 已
  `process.chdir(DATA_DIR)` 并把 `TMPDIR/TMP/TEMP` 指到可写目录解决。
- **`__dirname`/`__filename`**：ESM 无这两个变量；`main.js` 用 `import.meta.url` 自行派生。
  若 `dist-server` 内被打包进依赖 CJS `__dirname`，可能需在其构建侧注入 shim。

> 结论：`dist-server` 用到的 Node API 与原生模块 ABI 是模式 B 能否跑通的**头号验证项**，
> 需在目标内置 Node 版本上实测。跑不通时优先走下面的降级。

---

## 五、better-sqlite3 加载失败的降级策略

`better-sqlite3` 是 C++ 原生模块，最易在 nodejs-mobile 上因 ABI/交叉编译失败而崩。观测到
`NODE_MODULE_VERSION` / `invalid ELF` / `dlopen` / `cannot open shared object` 等错误时的降级顺序：

1. **切纯 JS / 内存库（保留端内模式 B，最省事）**：
   设 `MOTRIX_SQLITE_DRIVER=memory`（或 `pouchdb`/纯 JS 后端，具体键名以 `dist-server` 实际读取为准）。
   代价：任务历史/统计不持久化（重启丢失），核心下载功能不受影响。
2. **切远程模式 A（兜底，最稳）**：设 `MOTRIX_REMOTE=1`，App 不起端内后端，WebView 直接连
   一台跑 Motrix server 的 PC/NAS（填 URL + bridge 配对），手机当远程遥控客户端。
3. **正解（要持久化又必须端内）**：用 Android NDK 针对**内置 Node 的确切版本/ABI**重新交叉编译
   `better-sqlite3`，产物随 `dist-server` 一起 stage 进 assets。

> 选择权交用户：模式 B 全内嵌最忠实但依赖原生模块可编译；模式 A 牺牲「离线独立」换稳定。
> 无论选哪种，功能裁剪/行为变化需事先告知用户并经同意（红线）。

---

## 六、本地自检（桌面侧，可选）

在有 Node ≥16 的机器上、且已把 `dist-server/` stage 到位时：

```bash
# 起后端（用临时数据目录，避免污染）
MOTRIX_DATA_DIR=./_run MOTRIX_DOWNLOAD_DIR=./_run/dl MOTRIX_PORT=16999 node main.js
# 另开一个终端探测
MOTRIX_PORT=16999 node healthcheck.js
```

看到 `[MOTRIX_READY] {...}` 且 healthcheck 返回 `OK` 即代表引导逻辑与端口探测正常；
真机差异主要在节点版本/原生模块/外部存储路径，需在真机复验。
