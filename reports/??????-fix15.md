# 3B 首页标题与 m·o 徽标重叠修复 · fix15

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix15-debug.apk`
size 18,683,363 B　SHA-256：`BC82B3BDDC880886C24F87B1DC46523F95417C17EFB7B0E32A5E3425F8BBAF07`

## 现象
3B 仪表板左上角，页面大标题"仪表板"与固定的 m·o 徽标（及"切换边栏 / +"按钮）**重叠**。

## 根因
原版 UI 顶栏 `.window-chrome`（position:fixed，高约 40px，含 m·o 徽标、切换边栏、+ 按钮）**浮在所有内容之上**。宽屏（≥768）时侧栏展开、内容区从 x≈200 开始，正好避开左上角徽标，看不出问题；**窄屏（<768）侧栏自动收起、内容占满全宽**，内容区顶部（标题距顶约 17px）就钻到了固定顶栏底下 → 标题与徽标重叠。这也是"换一台更窄的手机才复现、K60 看不到"的原因。

## 修复（mobile-adapt.css §20）
```
@media (max-width: 767.98px) {
  /* 窄屏让出顶部固定工具栏(≈40px)+余量，标题落到其下方 */
  html.mtx-mobile [class~="min-h-0"][class~="flex-1"][class~="overflow-hidden"] {
    padding-top: 46px !important; box-sizing: border-box;
  }
  html.mtx-mobile .window-chrome { background: var(--mtx-bg,#14161a); box-shadow:0 1px 0 rgba(0,0,0,.25); }
}
```
仅窄屏生效，宽屏保持原布局。

## 验证
- 窄视口(700，等效侧栏收起的手机)：顶栏（m·o + 切换 + ＋）在上、分隔线、"仪表板"标题在其下方，**无重叠**（`fix15_dash700_tl.png`）。
- 宽屏(880，默认)：四卡多列正常，徽标与标题位置正常、**无回归**（`fix15_dashwide.png`）。

## 小队分工（本轮）
- qa-fixer：已交付《3B 手机端 UI 缺陷清单](reports/3B-UI缺陷清单.md)：7 条历史缺陷 + 8 组×38 项多宽度验收表。
- reverse-auditor：分析原版外壳/侧栏折叠断点与内容避让（docs/10-布局断点分析.md，进行中）。
- ui-ux-designer：出窄屏/竖屏顶栏避让 CSS 提案（renderer-overrides/proposal-topbar-uiux.*，进行中）。
- multiplatform-dev / perf-sec-builder：待命接后续代码/构建专题。
- Lead：负责整合、构建、真机验证与交付。
