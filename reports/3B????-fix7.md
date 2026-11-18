# 3B 界面优化 · fix7（状态栏 / 设置布局 / 按钮与保存回滚）

日期：2026-11-18　设备：Redmi K60（Android 14 / arm64-v8a，横屏）
产物：`dist/motrix-android-2.4.3-fix7-debug.apk`
SHA-256：`8bfbd2f5019eb2e4ffb758ff796879c70ff5254b190bdbe79246ae221595a5c7`

## 1) 顶部被手机状态栏遮挡 → 已修
- `MotrixWebActivity` 给 WebView 加 `WindowInsets` 监听，按系统状态栏 + 刘海（displayCutout）自动内缩：`paddingTop = 状态栏高度`，左右按刘海内缩，背景深色。
- 验证：仪表板截图显示状态栏独立成条，应用内容完整显示在其下方，不再重叠（`reports/v_dash.png`）。

## 2) 设置页布局问题 → 已修
- 原版设置分类卡是桌面尺寸：容器≥600px 时每张 min-height 170px、图标不限高，横屏一屏只露一行多、第三行被裁、需滚动。
- 覆盖（`mobile-adapt.css` §17）：分类卡高度压到 ~92px、图标限高 38px、标题与描述不再被大图标挤压；横屏较宽时改为 **4 列**，8 个分类两行全部露出、无需滚动。
- 验证：设置页现为 4 列两行，通用/外观/下载/BitTorrent + 集成/网络/高级/关于 全部可见、描述可读（`reports/v3_settings.png`）。

## 3) 设置按钮"不能用 / 保存后会回滚" → 已修（会话内持久化）
- 根因一：`defaultAppSettings()` 里 `runMode` 传了字符串 "normal"（应为数字枚举 1）、`speedLimitUnits` 是空对象（应为 kilo×4），导致 `/settings` 反序列化失败 → 多个设置弹窗数据异常/回滚。
- 根因二：`saveGeneralSettings / saveDownloadsSettings / updateSettings` 之前只返回 ok 不落库，重开弹窗又读到默认值 → 表现为"回滚"。
- 修复：`RpcDispatcher` 增加会话内设置存储（`savedGeneral / savedDownloads / appOverrides`），`get*Draft`/`getSettings` 优先返回已保存值；深合并更新。并把上述两处非法默认值改正。
- 验证：程序化测试 `saveGeneralSettings` 把 `notifyOnComplete` 由 True 改为 False 后，再次 `getGeneralSettingsDraft` 读回为 False（持久生效）。

## 4) 按钮形状（长方形 vs 方框不匹配）→ 已改
- `mobile-adapt.css` §16：图标按钮（仅含 svg）统一为正方形（aspect-ratio:1/1、40px 命中区、12px 圆角）；统一圆角；去掉默认方形 outline，改用与圆角一致的 inset 焦点描边，消除"长方形按钮里套方框"的错位感。

## 仍需你帮忙确认
- 具体还有哪些"按钮点了没反应"——请点到那一页/那个按钮告诉我，我按通道补实现。
- 应用内"主题切换/语言"等外观项是否能即时生效并保持。

## 验证截图
`reports/v_dash.png`（状态栏分离）、`reports/v3_settings.png`（4 列两行全露）。
