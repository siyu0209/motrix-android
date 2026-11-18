# 原生版统计图表（速度趋势）· fix29

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix29-debug.apk`
SHA-256：`6EC76D9BB4556D79F2C8FBCD5F149FCDD342D6B0B5F6F253558D1C34E3B8647B`

## 做了什么（P1-1 统计图表）
在原版（原生）首页顶部速度卡下方，新增「速度趋势（近 60 秒）」折线图卡片：
- 自绘轻量图表控件 `ui/SpeedChartView.kt`（**零第三方依赖**，纯 Canvas 绘制）：
  - 维护最近 60 个采样点，画 **下载(蓝) / 上传(紫)** 两条折线；
  - 自动纵轴缩放 + 峰值标注（右上角显示当前峰值速度）+ 4 条网格线 + 60s 时间轴；
- `MainActivity` 在速度流 `Engine.stat.collect` 里把每次 `downloadSpeed/uploadSpeed` 推给图表，实时刷新；
- `activity_main.xml` 在限速三档按钮下方插入图表卡片（卡片风格与整体一致）。

## 验证（真机 Redmi K60）
- 首页正确出现「速度趋势（近 60 秒）」卡片，含 ↓下载 / ↑上传 图例、纵轴峰值刻度、60s 轴标；页面无破版、无遮挡。
- 有流量时折线随采样绘制（截图 `reports/chart_speed2.png`：紫色上传曲线可见，蓝色下载线在 0 附近与上传线基本重合于基线）；100Mb 直链在高档下数秒即完成，采样回落 0。

## 影响
- 纯增量 UI，不影响下载内核与原有布局；后续可在此扩展为更长时间窗/吞吐柱状等。

## 验证截图
`reports/chart_speed.png`、`reports/chart_speed2.png`
