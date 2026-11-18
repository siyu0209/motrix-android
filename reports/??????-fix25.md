# 原生界面 · 限速三档快捷切换 · fix25

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix25-debug.apk`
SHA-256：`6CA7AAC28AA65C617F242B120105730E783F887EF847C7AD874B445C33F7870B`

## 做了什么
在原生版首页速度卡片下方，加了一排 **龟速 / 常速 / 高速** 三档快捷按钮，一键切限速，无需进设置。

- **高速**（默认）：不限速（下载/上传都不限）—— 按你要求，**默认就是最高速**；
- **常速**：下载 2 MB/s、上传 512 KB/s；
- **龟速**：下载 256 KB/s、上传 64 KB/s。

选中项高亮（实心蓝、白字），其余描边；选择会**记住**，重启后自动恢复并下发到引擎。

## 实现要点
- `activity_main.xml`：速度卡片下新增三个 `MaterialButton`（`btnSpeedTurtle/Normal/High`）。
- `MainActivity.kt`：`limitsFor(mode)` 映射三档速率；`setSpeedMode()` 持久化 + 高亮 + 调 `Engine.applyGlobalLimit`（下载/上传限速 K + 保持最大并发不变）；启动 3.5s 后把已保存档位下发（高速=0 无需下发）。
- `Prefs.kt`：新增 `speedMode`（默认 `"high"`）。
- `Engine.applyGlobalLimit` 走 aria2 `changeGlobalOption`（`max-overall-download-limit` / `max-overall-upload-limit`），实时生效、不影响已在跑的任务。

## 真机验证（Redmi K60 / bcfa3113）
用 aria2 RPC 直接读全局限速确认（`adb forward tcp:16800` + `aria2.getGlobalOption`）：
| 点击 | max-overall-download-limit | max-overall-upload-limit |
|---|---|---|
| 启动默认(高速) | `0`（不限） | `0`（不限） |
| 龟速 | `262144`（256 KB/s） | `65536`（64 KB/s） |
| 高速 | `0` | `0` |

截图：`reports/speed_home.png`（三档按钮，默认高速）、`speed_turtle.png`、`speed_high.png`。
