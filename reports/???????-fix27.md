# 原生维护 · 完整设置页（fix27）

产物：`dist/motrix-android-2.4.3-fix27-debug.apk`
SHA-256：`8440B3F567C6492E7858CEDE892FB80C92876C15976B865548F3EF29364A63E9`　大小：19,763,413

## 做了什么
把原生「设置」页从"只有目录/并发/限速/做种/RPC"扩成**完整下载 + BT 设置页**，新增可调项：
- 单服务器最大连接数（1–16）
- 分片数 split（1–16）
- BT 最大连接数（bt-max-peers）
- 监听端口（如 6881-6999，BT/DHT 端口段）
- 启用 DHT（开关）
- （原有：下载目录、最大同时下载数、下载/上传限速、做种时长、RPC 端口/密钥）

两类生效方式：
- **即时生效**（不必重启）：最大同时下载数、下载/上传限速 → aria2 `changeGlobalOption`。
- **保存并重启引擎后生效**：连接数/分片/BT连接数/监听端口/DHT → 由 `Engine.restart()` 用新设置重建 aria2 进程（会中断当前下载队列，界面有明确按钮「保存并重启引擎」）。

## 代码改动
- `engine/Models.kt`：`EngineSettings` 增 `maxConnectionPerServer/split/btMaxPeers/dhtEnabled/listenPortStart/listenPortEnd`。
- `engine/Aria2Process.kt`：启动参数改为读这些设置（替换原写死的 16/16/true/6881-6999/128）。
- `Prefs.kt`：load/save 增读写上述字段。
- `engine/Engine.kt`：新增 `restart(onDone)`（stop→start(新设置)）。
- `SettingsActivity.kt` + `res/layout/activity_settings.xml`：新增 4 个输入 + DHT 开关 + 「保存并重启引擎」按钮。
- `strings.xml`：对应文案。

## 真机验证（Redmi K60 / bcfa3113）
- 经 ⋮→设置 进入设置页，OCR 确认新字段齐全：单服务器最大连接数=16、分片数=16、BT 最大连接数=128、监听端口=6881-6999、启用 DHT=开。
- 点「保存并重启引擎」→ aria2 重启后 `getVersion`=1.37.0；`getGlobalOption` 读回 **split=16、max-connection-per-server=16、bt-max-peers=128、listen-port=6881-6999、enable-dht=true** → 设置成功作用于新引擎进程。
- 截图：`reports/settings_fix27.png`、`settings_fix27_bottom.png`。
