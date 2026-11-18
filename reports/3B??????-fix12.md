# 3B 手机交互修复 batch · fix12c

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix12c-debug.apk`
SHA-256：`CBAE66F40068E8FC2B55CCBE48EF3DAB16866EDA89A02433AC89F034C7EB2E04`（size 18,682,559）

## 针对用户反馈逐条修复
| # | 反馈 | 处理 | 结果 |
|---|---|---|---|
| 1 | 仪表板/下载页不能向下滑 | 定位到主内容滚动层被 `overflow-hidden` 裁掉，恢复 `overflow-y:auto`（§19） | 实测滑动后滚动容器 scrollTop=186px，**可滚动** |
| 2 | 收起侧边栏/进子页/设置退出后回不到原页 | ① WebView 硬件返回键接管（canGoBack→goBack）；② 底部居中注入「‹ 返回 / ⌂ 首页」悬浮按钮 | 返回链路可用 |
| 3 | 进入插件页无法退回 | 手机不需要，**侧栏「插件」入口隐藏** | 侧栏现为 仪表板/下载/Trackers/通知/设置 |
| 4 | 设置里「集成」等桌面项按手机裁剪 | **隐藏设置「集成」卡**（浏览器扩展/CLI 桌面专用） | 设置仅剩 通用/外观/下载/BitTorrent/网络/高级/关于 |
| 5 | 关于原作者等桌面内容 | 隐藏含 `Dr_rOot`/原作者 的行 | 关于精简 |
| 6 | 文案超出方框边界 | 设置卡文字 `overflow-wrap:anywhere` + 3 行 `-webkit-line-clamp` 截断 | 文案不越界 |
| 7 | 仪表板标题与 M·O 重叠 | 顶栏 flex：logo 固定不缩、标题 `min-width:0; ellipsis` 截断 | 不再重叠 |
| 8 | 限速板块不能快捷切换模式 | 桥接 `RpcDispatcher` 增加 turtle 态维护（off/on/auto），`getSpeedLimitState` 回真实值、`commandUpdateSettings` 落库并 `changeGlobalOption` 实际限速 | 实测点「低速模式」→ 模式文字变「低速模式」，aria-pressed 正确切换 |
| 9 | 点右上设置退出不能回仪表板 | 同 #2（返回+首页悬浮） | 可回 |

## 验证方式
真机（Redmi K60，adb 全权限）用 `adb input` 真实点击/滑动 + `screencap` 截图 + `evaluateJavascript` 读 DOM/触发事件双重验证。截图：`reports/f12c_dash.png`、`f12c_settings.png`。

## 仍待办（下一批）
- 「传输」卡底部数值在视口底缘被裁（滚动可见，属布局节奏）；可再压卡片高度。
- 下载页「字幕被遮盖」、下载多时列表滚动：需专门进下载页复核（滚动层已通，待截图确认）。
- 任务详情（3B 点行未能打开 inspector，原生版已有详情）。
- .torrent 文件勾选补 bencode；签名 release 包；Resources 警告清理。
