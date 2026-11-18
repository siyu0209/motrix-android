# 状态栏"顶部被切掉"修正 · fix11（改回正常状态栏）

日期：2026-11-18　设备实测：Redmi K60（Android 14）
产物：`dist/motrix-android-2.4.3-fix11-debug.apk`
SHA-256：`BC6020C7E4B563117002AFC924E5BBD1D27C5EB82AD8570864A34B1A7A50F708`

## 问题
fix10 用了"全面屏(edge-to-edge) + 手动 padding"，结果顶部多出一条空白/纯黑带，看起来像**把上面整个切掉了**。

## 这次怎么改（改回正常观感）
- **去掉 edge-to-edge 与手动 padding**，回到标准布局：**系统状态栏正常显示，应用内容紧接在其下方**——不遮挡、也不留空白条。
- 额外为 **Android 15** 加了主题开关 `android:windowOptOutEdgeToEdgeEnforcement=true`（`res/values-v35/themes.xml`）：A15 不再强制全面屏，保持同样"状态栏在上、内容在下"的正常外观，换手机也不会重现遮挡/空白。

## 验证（截图见 reports/）
- `fix11_native.png`：状态栏（3:30 等）→ 紧接 Motrix 标题栏，无空白条、无遮挡。
- `fix11_3b.png`：状态栏 → 紧接 3B 顶栏（logo m·O▾ / 侧栏切换 / 新建+ / 标题 仪表板），在 y≈90 处干净衔接。
- 备注：3B 横屏最左缘的窄黑条是该机**挖孔/曲边屏的系统安全区 letterbox**，不是应用 UI。

## 后续
状态栏这一项按"正常系统状态栏"定稿。继续做全站巡检与欠账（框选不匹配、点不动按钮、.torrent bencode、签名 release、Resources 警告）。
