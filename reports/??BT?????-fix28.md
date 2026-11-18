# 原生版 BT 多文件勾选 · fix28

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix28-debug.apk`
SHA-256：`FC128D158D38B84C9A0066F88B54C91018F9D2DE36B3FA71CA1657F92B837336`

## 做了什么
从此前「选 .torrent 只能整包下载」升级为**像原版一样勾选要下哪些文件**：
- 新增纯 Kotlin 的 **bencode 解析器**（`engine/Bencode.kt`）与 `TorrentParser`：读取 `.torrent` 里的 `info.files[{length,path}]` / 单文件 `info.length+name`。
- 原生新建任务选种子（或分享 `.torrent` 打开）时：多文件 → 弹「选择要下载的文件」多选列表（显示每个文件大小、默认全选）；只勾选的才下载。
- 加任务时按勾选生成 aria2 的 `--select-file` 参数下发。

## 验证（真机 Redmi K60 + aria2 RPC 实测）
1. 现造一个三文件种子 `mtx_test.torrent`（a.txt 1.17KB / b.bin 10KB / c.txt 1.46KB），推入后触发：弹出「选择要下载的文件」三行、带大小、全勾选 ✔。
2. 手动**取消勾选 b.bin** → 点「下载」→ aria2 `tellActive` 读回：
   `mtx_test :: a.txt sel=true | b.bin sel=false | c.txt sel=true`
   → **只下载所选文件，未选的不下载**，与预期完全一致 ✔。

## 影响
- 只新增能力，不影响原有 HTTP/磁力/整包下载路径。
- 至此原生版 P0 对照 3B 的主要功能补齐：限速三档(fix25)、Tracker 管理(fix26)、完整下载/BT 设置页(fix27)、**BT 多文件勾选(fix28)**。

## 验证截图
`reports/torrent_select.png`（多选对话框）、`reports/torrent_select_unchecked.png`（取消 b.bin）、`reports/torrent_added.png`（添加后列表）
