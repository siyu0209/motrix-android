# 正式签名 Release 包 + .torrent 外部打开 · fix31

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-release-signed.apk`
SHA-256：`A1A409A8ECD2AB97D4F1345718873E515847FF213CD455DC68DB27805CB741DE`
大小：16,151,971 字节（约 15.4 MB）

## 做了什么
1. **生成发布签名密钥**：`android/app/motrix-release.jks`（RSA 2048，别名 `motrix`，口令 `Motrix@2026`，有效期 10000 天）。
   - ⚠️ 这个 .jks 和口令**务必保存好**：以后升级安装必须用同一密钥签名，否则会因签名不一致无法覆盖安装。
2. **配置 Release 签名构建**（`app/build.gradle.kts` 增加 `signingConfigs.release`，`buildTypes.release` 关闭混淆/资源压缩以防反射/RPC 通道被裁）。
3. **`.torrent` 外部打开**：MainActivity 增加 `content://` 与 `file://` 的 `application/x-bittorrent` intent-filter，文件管理器里点 `.torrent` 可直接用本 App 打开并进入多文件勾选。

## 「让手机不报毒」说明（关键）
- **根因**：之前发的是 **debug 包**（`android:debuggable=true`）——这正是安全软件/系统最常报"高风险/可疑"的原因。
- **本次是全 Release 包**：
  - `apksigner verify` 通过，签名者 `CN=Motrix Android ...`，证书 SHA-256 `b27e938ce6e72cee82387d7b3452f59dc6f2b03ada87e09cc45e0eee08eddbee`；
  - `aapt2 dump badging` **无 `debuggable` 标志**（非可调试），Release 签名、正常 Release 构建。
  - 这两点是能把"报毒/高风险"降到最低的核心。
- 仍可能遇到的是**"未知来源/未经应用商店安全检测"**这类系统通用提示（任何非应用商店签发的 App 都会提示），选「仍要安装 / 继续安装」即可，它**不是病毒告警**。
- 若个别品牌杀软的启发式仍报警，可在其"病毒扫描/应用管控"里把本 App 加入信任；如需我进一步处理（例如自定义签名主体名、减少敏感权限声明如 WRITE_EXTERNAL_STORAGE maxSdk28 等）可继续。

## 安装说明（本次）
- 构建机上 `adb install` 当前被 MIUI 拦（`INSTALL_FAILED_USER_RESTRICTED`，卸载重装后"USB安装"被重置）。已把包 push 到手机 **`/sdcard/Download/motrix-release-signed.apk`**，请手动点开安装一次；或重新打开「USB安装」后告诉我，我再自动安装并复测。
- 兼容性：minSdk 24（Android 7+），targetSdk 34，compileSdk 35。
