# aria2（aria2c）第三方组件说明

本项目在 Android 端内嵌 **aria2** 命令行下载引擎（可执行文件 `aria2c`），
用于还原 Motrix 的下载 / BT / 磁力 / 限速等核心引擎能力。

- 上游项目：https://github.com/aria2/aria2
- 许可证：**GNU General Public License v2.0 (GPLv2)**
  全文：https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
- 二进制来源：**社区预编译**（首选 Termux 官方仓库的 deb 包，`aarch64`=arm64-v8a、`arm`=armeabi-v7a）。
  aria2 官方 GitHub Release 仅提供源码包与 Windows 构建，**不发布 Android 预编译二进制**。

## 合规要点（GPLv2）

1. 随 App 分发 aria2 二进制时，必须同时提供对应源码的获取方式（下载地址或书面要约），
   不得对 aria2 本身施加比 GPLv2 更严格的限制。
2. 本项目以**独立进程**方式调用 `aria2c`（通过命令行 / JSON-RPC 交互），
   应用主体与 aria2 之间以进程边界隔离。
3. 若后续修改了 aria2 源码，需同样以 GPLv2 公开修改后的源码。

## 每个 ABI 的实际来源与哈希

见同目录 [`manifest.json`](./manifest.json)（由 `scripts/fetch-aria2-android.ps1` 生成）。
其中 `verified=false` 表示该二进制来自社区、其 sha256 尚需人工与可信发布源核对。

## 候选取件地址（供人工取件 / 复核）

- https://packages.termux.dev/apt/termux-main/pool/main/a/aria2/  —— 直接取 `*_aarch64.deb` / `*_arm.deb`
- https://github.com/termux/termux-packages —— Termux 打包配方（可自行按 NDK 交叉编译）
- https://github.com/aria2/aria2/releases —— 官方（仅源码 / Windows，Android 不可直接用）

> 取件后可用：`pwsh ./scripts/fetch-aria2-android.ps1 -LocalArm64 <arm64文件> -LocalArmv7 <arm文件>` 落地。
