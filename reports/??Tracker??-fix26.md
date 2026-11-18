# 原生维护 · Tracker 管理（fix26）

产物：`dist/motrix-android-2.4.3-fix26-debug.apk`
SHA-256：`4D7EB51D3C6E53C90D709FE9F556D7B7297A1681D1212DFDF04509E370E76325`　大小：19,760,201

## 做了什么
原生简洁版菜单新增 **「Tracker 管理」**（右上角 ⋮ → 第一项）：
- 打开时**自动读取当前引擎的 bt-tracker 列表**并预填（默认内置 30 个公共 tracker）；
- 编辑框里**每行一个**（udp:// / https:// / http:// 均可），留空表示不额外注入；
- 点「保存」→ 通过 aria2 `changeGlobalOption(bt-tracker)` **即时生效**（无需重启引擎/重下任务），并持久化。

## 代码改动
- `engine/Aria2Rpc.kt`：新增 `getGlobalOption()`。
- `engine/Engine.kt`：新增 `queryTrackers(cb)`（读引擎当前 tracker）、`applyTrackers(csv,onResult)`（changeGlobalOption + 存 SP）。
- `MainActivity.kt`：菜单 `action_trackers` → `showTrackers()` 对话框（多行预填 + 保存）。
- `res/menu/menu_main.xml`：加 `action_trackers`；`res/values/strings.xml`：加 `menu_trackers`/`trackers_saved`。

## 真机验证（Redmi K60 / bcfa3113）
- 菜单出现「Tracker 管理」（7 项：Tracker 管理 / 下载位置 / 全部暂停 / 全部开始 / 清除已完成 / 打开原版界面(3B) / 设置）。
- 点开对话框：标题「Tracker 管理」，输入框**已预填引擎当前 30 个 tracker**（udp/https/http 混合）→ `queryTrackers` 生效。
- 点「保存」后经 aria2 RPC `getGlobalOption` 读取：`bt-tracker` 长度 1188、首条 `udp://tracker.opentrackr.org:1337/announce` → `applyTrackers/changeGlobalOption` 生效。
- 截图：`reports/menu1.png`、`reports/tracker_dialog.png`、`reports/tracker_after.png`。
