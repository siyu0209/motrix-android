# 3B 顶栏重叠修复（对齐官方断点）· fix16

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix16-debug.apk`
size 18,683,675 B　SHA-256：`D9DEAAFC7CBC49C9EA147C521E90ADFDCD54EBDB7A84C25F241A1523FAB31255`

## 现象
3B 仪表板左上角「仪表板」大标题与固定 m·o 徽标（及切换边栏 / + 按钮）在窄屏重叠。

## 依据（reverse-auditor《docs/10-布局断点分析.md》）
- 顶栏 `WindowChrome`：`position:fixed; top0; z-30; 透明`，**高 40px**；侧栏折叠断点 **768px**。
- 官方两轴避让：**640–767** 用 `padding-inline-start: var(--window-chrome-safe-area-leading)=160px` 让标题与徽标**同一行**横向错开（顶栏透明，不竖向下移）；**<640** 用 `padding-block-start:44px`（触屏 56px）把标题**移到顶栏之下**。

## 修复（mobile-adapt.css §20，对齐 768/640/56 三档）
- 640–767：`[class~="min-w-0"]:has(>h1/h2){ padding-inline-start:160px }` —— 标题与徽标同行、右侧排布。
- <640：内容容器 `padding-top:44px`；`pointer:coarse`（触屏）56px —— 标题下移顶栏之下。
- 顶栏 `.window-chrome` 给底色，避免透明叠字。

## 验证
- **窄屏 700**：`m·o ⌄ / 切换 / ＋ / 仪表板` 同一行，间距清晰、**无重叠**（`fix16_700_tl.png`）。
- **窄屏 600**：标题在顶栏下方，与徽标不重叠（`fix16_600.png`；因"固定宽度模拟"会压缩高度，纵向留白偏大属模拟假象）。
- **宽屏**：徽标与标题同行、无重叠、无多余空白，**无回归**（`fix16_wide_tl.png`）。

## 小队进展
- qa-fixer：《3B-UI缺陷清单》已建（7 条历史 + 8×38 验收表）。
- reverse-auditor：《docs/10-布局断点分析》完成（顶栏 40/断点 768/640/leading160，逐条 file:line）。
- ui-ux-designer：窄屏/竖屏顶栏避让 CSS 提案（进行中）。
- Lead：整合 → 构建 → 真机验证 → 交付（本轮 fix16）。
