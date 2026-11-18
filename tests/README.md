# tests/ — QA 校验工具与验收说明

本目录是角色9（qa-fixer）的测试资产，配套文档 `docs/06-QA测试计划.md`。
当前包含：

| 文件 | 作用 |
|---|---|
| `validate-deliverable.ps1` | 交付物完整性校验脚本（纯 PowerShell，可重复运行）。扫描项目交付树，校验关键文件是否齐全，输出 PASS/FAIL 清单与缺失项。 |
| `README.md` | 本说明：怎么跑校验、怎么据此出验收报告。 |

---

## 1. 校验脚本做什么

`validate-deliverable.ps1` 是**入场闸门**：文件不齐就不进入功能测试。它按 `BLUEPRINT.md` 的目录契约逐项检查：

- **根文件**：`BLUEPRINT.md`（必需）、`README.md`（可选）
- **docs 7 份**：`00-需求转译.md` / `01-源码审计.md` / `02-架构方案.md` / `03-构建指南.md` / `04-差距与路线图.md` / `05-交付清单.md` / `06-QA测试计划.md`
- **android 工程关键文件**：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`app/src/main/java`（目录）、`app/src/main/res`（目录）、`app/src/main/assets`（目录）、`assets/nodejs-project`（目录），以及「至少一个 Java/Kotlin 源文件」
- **scripts 4 个**：`install-toolchain.ps1`、`fetch-aria2-android.ps1`、`build-renderer.ps1`、`build-all.ps1`
- **renderer-overrides**：目录非空 + `mobile-adapt.css`、`mobile-adapt.js`、`README.md`、`screens.md`
- **third_party/aria2**：目录 + `NOTICE.md`
- **tests 自身**：`validate-deliverable.ps1`、`README.md`（可选自检）

输出规则：

- 每项打 `PASS` / `FAIL` / `WARN`。**必需项缺失 = FAIL**；可选项缺失 = WARN（不影响总判定）。
- 汇总 `总计 / PASS / FAIL / WARN`，并单列「缺失项（必须补齐）」。
- 退出码：全部必需项通过 → `0`；有必需项缺失 → `1`；根目录不存在 → `2`。

> 注意：脚本只校验「存在性/非空」，不校验内容质量。功能、性能、合规等仍必须按 `docs/06-QA测试计划.md` 第 2~7 节在真机执行。

---

## 2. 怎么跑校验

### 2.1 最简（在项目根目录下）

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\validate-deliverable.ps1
```

或 PowerShell 7：

```powershell
pwsh -File .\tests\validate-deliverable.ps1
```

脚本默认把「自己所在目录的上一级」当作项目根（即 `motrix-android\`），因此在任意工作目录以脚本绝对路径调用都可以。

### 2.2 指定根目录（可移植 / CI）

```powershell
pwsh -File .\tests\validate-deliverable.ps1 -Root "C:\工作日志\projects\motrix-android"
```

### 2.3 同时产出 Markdown 报告

```powershell
pwsh -File .\tests\validate-deliverable.ps1 -ReportPath .\reports\deliverable-check.md
```

> `-ReportPath` 支持相对/绝对路径；父目录不存在会自动创建。建议写到 `reports/`（最终由 Lead 汇总归档），校验脚本本身无副作用、可反复运行。

### 2.4 在 CI / 一键脚本里用退出码判定

```powershell
pwsh -File .\tests\validate-deliverable.ps1
if ($LASTEXITCODE -ne 0) { Write-Error "交付物不齐，终止流水线"; exit 1 }
```

---

## 3. 怎么看输出

- 逐组（根文件 / docs / android / scripts / renderer-overrides / third_party / tests）列出每项状态与相对路径。
- 末尾 `RESULT: PASS` 表示关键交付物齐全，可进入功能测试；`RESULT: FAIL` 表示有必需项缺失，看「缺失项」清单逐条补齐后重跑。
- `WARN` 是可选建议项（如 `README.md`、`assets/renderer-web` 在 stage 前的过渡态），补齐更好但不阻断。

---

## 4. 怎么据此出验收报告

1. **第一步（完整性闸门）**：跑 `validate-deliverable.ps1`，把输出/`-ReportPath` 生成的表格作为报告「交付物完整性」章节。要求 `RESULT: PASS`（或有 Lead 认可的豁免清单）。
2. **第二步（功能与适配）**：按 `docs/06-QA测试计划.md` 第 2~4 节用例表在设备矩阵上执行，记录每条 `PASS/FAIL`，未过项登记缺陷号。
3. **第三步（性能/权限/稳定性/双模）**：执行第 4~7 节，填入指标实测值（冷启动 ms、PSS MB、保活结果、权限清单等）。
4. **第四步（对照验收门槛）**：逐条核对第 11 节「出场准则」：P0 100% PASS、P1 ≥95%、Blocker/Critical 清零、改造引入 bug 100% 修复、性能与合规达标、双模可用。
5. **汇总成报告**，建议结构：

```
# Motrix 安卓移植 — 验收报告
- 构建号 / APK 版本 / 测试日期 / 设备矩阵
## 1. 交付物完整性（贴 validate-deliverable.ps1 报告表）
## 2. 功能与适配（用例结果矩阵：编号 / 结果 / 缺陷号）
## 3. 性能指标（实测 vs 门槛）
## 4. 权限与合规（权限清单 + 三项 P0 结论）
## 5. 稳定性与异常（STAB 结果）
## 6. 双模切换（DUAL 结果）
## 7. 缺陷汇总（原生 / 改造引入分类，修复与回归状态）
## 8. 结论与遗留项（对照出场准则逐条打勾）
```

---

## 5. 已知限制

- 只查存在性，不查内容正确性/质量；不替代真机功能测试。
- 交付树由多个角色并行填充，运行时刻可能处于中间态，`FAIL` 需结合时间点判断（属正常过渡还是真缺失）。
- 目录/文件命名若在实现阶段调整（如 renderer-overrides 文件名、nodejs 启动脚本名），需同步更新脚本内的检查项。`nodejs-project/main.js`、`assets/renderer-web`、`README.md` 等已按 BLUEPRINT 约定纳入（部分为可选 WARN）。
