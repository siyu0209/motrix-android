# 3B（原版 Motrix 界面）优化报告 · fix6

日期：2026-11-18　设备：Redmi K60（Android 14 / arm64-v8a）
产物：`dist/motrix-android-2.4.3-fix6-debug.apk`
SHA-256：`ab999ad846529c13b0a1b8284f0a646992c968ba533b1bd6a75533149c91f1b8`

## 本轮修复的问题

| # | 问题 | 根因 | 修复 |
|---|------|------|------|
| 1 | Trackers 页整页「此页面暂时无法显示」错误边界 | BridgeServer 返回裸字符串未加 JSON 引号，`getTrackerSyncStatus:"idle"` 解析失败；且多条 tracker 通道未实现 | jsonRaw 对 String 走 `JSONObject.quote`；补齐 getTrackerList/getTrackerSyncStatus/getTrackerSources/getTaskTrackerPlan |
| 2 | 插件页整页错误边界 | 插件相关通道全未实现，返回 null 导致组件 `items.map` 崩溃 | 补齐 listPlugins/listPluginGrants/listRegistryPlugins/getPluginConfig/getPluginGrants/getPluginLogState/getPluginLogs/getPluginHookRank/checkPluginCompatibility |
| 3 | 设置>集成 页整页错误边界 | 6 个桥接/工具探测通道未实现 | 补齐 bridge:listTrusted/listPaired/listPendingPairRequests、bridge:getStatus、getCliToolStatus、getFfmpegDetection、getFfmpegInstallStatus |
| 4 | 设置>通用 弹窗红条「无法更新目录记录，请刷新确认当前状态」 | GeneralSettingsAppSchema.runMode 是数字枚举，返回了字符串 "normal"，schema 校验失败 | runMode 改为数字 `1` |
| 5 | 设置>下载 弹窗红条 + 默认保存位置为空 | ① speedLimitUnits 空对象（必填四键）② engine.minSplitSize=0 低于下限 1048576 | speedLimitUnits 补全 kilo×4；minSplitSize 改 1048576 |
| 6 | 3B 界面手机比例不对（文字过小/挤压） | renderer-web 的 index.html 没有 viewport meta | 注入 viewport + 移动适配 CSS/JS；横屏走桌面布局(>600px)，竖屏单列 |

## 已验证正常（真机截图 reports/3B_*.png）

- 仪表板：引擎就绪(aria2 v1.37.0)、限速、上传/下载曲线、活跃任务、传输统计，均正常
- 下载：`全部下载 2/2`，10Mb.dat 100% 已完成，实时刷新
- 通知页、新建任务弹窗：正常
- 设置九类：通用 / 外观 / 下载 / BitTorrent / 集成 / 网络 / 高级 / 关于 全部正常、无红条、无错误边界

## 已知剩余（均为小项，不阻塞使用）

1. 3B 任务详情：点击列表行/路由未打开右侧检查器（原生版已有完整详情页，可替代）
2. 设置落地页：卡片图标与文字略挤、第三行需滚动
3. 下载列表：左侧滚动条压住行首复选框
4. 仪表板「活跃任务」卡底部统计行略微被裁
5. 关于页「应用更新」提示无法更新＝当前环境无外网，正常

## 其他待办（非本轮）

- .torrent 种子文件列表需补 bencode 解析
- 签名 release 包（当前为 debug 包）
- Resources$NotFoundException 良性警告清理
- 原生版「打开文件」真机点一次确认
