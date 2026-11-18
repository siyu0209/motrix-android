# 构建尝试记录（reports/build-attempt.md）

> 角色：perf-sec-builder（构建打包）。时间：2026-11-18（Asia/Shanghai）。
> 目标：在仓库就绪前提下试跑 `pnpm install` 并记录结果。结论：**仓库未就绪，阻塞在源头，本轮无法进入 pnpm 构建**。

---

## 1. 结论速览

| 检查项 | 结果 | 备注 |
|---|---|---|
| Motrix 源码仓库目录 `C:\工作日志\projects\Motrix` | 存在，但**工作树为空** | 只有隐藏 `.git`，`git log`/`ls-tree` 报 `HEAD 无效`、`current branch appears to be broken` |
| `package.json` | **不存在** | 无法 `pnpm install` |
| `pnpm-lock.yaml` | **不存在** | — |
| `pnpm` 命令 | **未安装** | `无法将"pnpm"项识别为 cmdlet...` |
| `node` | v26.3.0 ✔ | 运行时可用 |
| `java`（PATH） | Oracle JDK **1.8** ✘ | 需 JDK17；`JAVA_HOME` 现指向某 jdk-21 |
| `ANDROID_HOME` | 空 ✘ | 未装 Android SDK |
| `motrix.zip`（同级 `projects\motrix.zip`） | 8.7 MB，但**中央目录损坏**（`找不到中央目录结尾记录`），非有效 zip | 不能直接解压得到源码 |

**一句话卡点**：源码仓只有一个残缺的 `.git`，没有工作树；`projects\motrix.zip` 也解不开。缺源码 → 缺 `package.json` → `pnpm install` 无从谈起。此外本机无 pnpm、无 Android SDK、JAVA_HOME 非 17。

---

## 2. 实际执行记录（原样）

```
package.json exists: False
pnpm-lock exists: False
--- pnpm -v ---
pnpm error: 无法将"pnpm"项识别为 cmdlet、函数、脚本文件或可运行程序的名称...
--- attempt pnpm install ---
install error: 无法将"pnpm"项识别为 cmdlet、函数、脚本文件或可运行程序的名称...
--- node -v ---
v26.3.0
```

```
git -C C:\工作日志\projects\Motrix log --oneline -1
  fatal: your current branch appears to be broken
git -C ... ls-tree --name-only HEAD
  fatal: Not a valid object name HEAD
```

---

## 3. 已交付（本轮实际写出的文件）

| 文件 | 说明 |
|---|---|
| `scripts/fetch-aria2-android.ps1` | 取 arm64-v8a / armeabi-v7a 的 `aria2c`，多候选源 + sha256 记录 + NOTICE/manifest |
| `scripts/install-toolchain.ps1` | 幂等装 JDK17 + cmdline-tools + platform-tools + build-tools;34 + platform;34 + ndk;26.x，写环境变量 |
| `scripts/build-renderer.ps1` | `pnpm install` + `pnpm build:server`，stage `dist/renderer-web` / `dist/server` 到 assets |
| `scripts/build-all.ps1` | 一键串：toolchain→renderer→aria2→staging→`gradlew assembleDebug`，带日志与错误检查 |
| `docs/03-构建指南.md` | 零基础完整构建 + 常见报错（含 better-sqlite3 / Rust 交叉编译） |
| `third_party/aria2/NOTICE.md` | aria2 GPLv2 来源与合规说明（脚本也会生成/追加） |
| `reports/build-attempt.md` | 本文件 |

> 这些脚本**已通过 PowerShell 语法解析**（见 §4），但因缺源码、缺 pnpm、缺 SDK，**未做端到端实跑**。

---

## 4. 脚本校验（本机已做）

- **语法解析**：四个 `.ps1` 均用 PowerShell 官方 Parser（`[System.Management.Automation.Language.Parser]::ParseFile`）解析，**4/4 通过**。
- **编码坑修复**：本机宿主为 **Windows PowerShell 5.1**，UTF-8 无 BOM 会被按 GBK 解码导致中文串截断/语法报错；已将全部脚本存为 **UTF-8 with BOM**，5.1/7 均正常解析。
- **运行期 bug 修复**：`$PSScriptRoot` 在 **5.1 的参数默认值阶段为空**，原先写在 param 默认里的路径推导会 `Split-Path 空字符串` 报错；已改为在脚本体（`$ErrorActionPreference` 之后）解析默认路径。
- **干跑验证**：
  - `build-renderer.ps1 -RepoRoot C:\no_such_repo_xyz` → 正确走到「仓库根未找到 package.json」守卫并 `exit 1`（中文正常、无编码乱码）。
  - `fetch-aria2-android.ps1 -LocalArm64/-LocalArmv7 <dummy>`（隔离临时 `-Root`，走本地分支，不联网）→ `exit 0`，正确生成 `third_party/aria2/<abi>/aria2c`、`manifest.json`（含各 abi sha256、verified=false）、`NOTICE.md`。
- **未验证**：`install-toolchain.ps1`（会真实下载 JDK/SDK/NDK，本轮按约束不执行）、`build-renderer.ps1` 的 pnpm 实际构建、`build-all.ps1` 的 gradle 打包。这些需在「源码就绪 + 工具链装好 + 真机/模拟器」后跑。

---

## 5. 解除阻塞的下一步（给 Lead / 用户）

1. **让源码就绪**（最关键，二选一）：
   - 重新 `git clone https://github.com/agalwood/Motrix`（建议 pin 到 v2.4.3 motrix-turbo 对应 tag/commit）到 `C:\工作日志\projects\Motrix`；或
   - 提供一份**完整可解压**的源码包（现有 `motrix.zip` 已损坏，需重新提供）。
2. 装 pnpm：`corepack enable` 或 `npm i -g pnpm`（`build-renderer.ps1` 也会自动处理）。
3. 装工具链：`pwsh ./scripts/install-toolchain.ps1`（需联网，下载 JDK17/SDK/NDK，约数 GB）。
4. 再跑：`pwsh ./scripts/build-all.ps1`，日志落 `reports/build-all-<时间戳>.log`。

---

## 6. 遗留风险（诚实标注）

- **R1｜源码缺失/损坏**：仓库无工作树、zip 损坏，是当前唯一硬阻塞，非脚本能自愈。
- **R2｜better-sqlite3 交叉编译**：未验证 Android NDK 交叉编译链路，是最大技术硬骨头；降级方案（纯 JS sqlite / 远程模式）已写入构建指南 §4.4。
- **R3｜nodejs-mobile Node 版本**：本机 Node 为 26，端内运行时可能更低，server 新 API 兼容性未验证（指南 §4.6）。
- **R4｜aria2 二进制来源**：官方不发布 Android 版，社区 deb 的 sha256 需人工复核（`manifest.json` 标 `verified=false`）；GPLv2 合规需随包提供源码获取方式。
- **R5｜端到端未验证**：JDK17/SDK/NDK 均未在本机安装，`gradlew assembleDebug` 从未实跑，APK 产物未生成。
