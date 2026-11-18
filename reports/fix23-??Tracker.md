# fix23 · 扩展公共 Tracker 列表（提升冷门磁力首次连 peer 速度）

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix23-debug.apk`
SHA-256：`592E9780A2A60927883633363F60004E06F90BE01969E09507A649A08E9E5D3F`　大小：18,716,561 B

## 改了什么
`engine/Aria2Process.kt` 的 `--bt-tracker=` 从原来 5 个公共 tracker 扩到 **30 个**（UDP 为主 + HTTPS/HTTP 备用，取自社区长期在线的公开 tracker 列表）。其余参数不变。

## 对其它下载有没有影响
**没有。** `--bt-tracker` 只在 **BitTorrent / 磁力** 任务里用于"找 peer"；对 HTTP/HTTPS/FTP 直链下载完全无影响，也不增加体积（就是一段字符串）。

## 实测（本机 K60，ubuntu 热门种子）
- 连上 **31 个 peer**，速度跑到 **6.3 MB/s**（30 秒内），比只加 5 个 tracker 时更快连满。
- 测试任务已删除、残留文件已清理，不占空间。

## 适用场景
- 对**冷门种子 / 首次连 peer 慢**的磁力更明显；热门种子本来就连得快，差别不大。
- 若某磁力仍 0 速，多为"没人做种"或"当前网络封 P2P"，非客户端问题（Tracker 也救不了无人做种的种子）。
