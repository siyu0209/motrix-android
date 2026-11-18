# 删除任务失灵修复 · fix21

日期：2026-11-18　产物：`dist/motrix-android-2.4.3-fix21-debug.apk`
SHA-256：`7564FC174CD4CF7C1813F7CA6F33269D167B0F651239AF2A3D277CE52925004E`

## 根因（已定位并实测复现）
删除任务时，App 先调 aria2 `remove/forceRemove`，再调 `removeDownloadResult`。但对**已完成 / 错误 / 已暂停**的任务，aria2 的 `remove/forceRemove` 会报错（"Cannot remove completed download"），异常导致后面的 `removeDownloadResult` 根本没执行 → 任务既没从列表消失、又返回 `{"ok":false,"error":{"code":"engine"}}`。
用户看到的就是"删除按钮大范围失灵"（绝大多数要删的都是已完成任务）。

实测复现（修复前）：
- `removeTask(gid=254148ee…, status=completed)` → `{"ok":false,"error":{"code":"engine"}}`，列表仍是 1 条。

## 修复
两处都把"remove"和"removeResult"拆成独立 try，任何一步失败都忽略，**保证 `removeDownloadResult` 一定执行**（它才是把任务从列表清掉的关键）：
- `web/RpcDispatcher.kt` → `removeOne(gid)`（3B 原版界面用到，`removeTask`/`removeTasks` 都走它）
- `engine/Engine.kt` → `remove(gid)`（原生简洁版用到）

## 验证（真机 fix21）
- 新建 http 任务下载 `10Mb.dat` → 12s 后 `completed count=1`
- 调 `removeTask` → `removeResp={"ok":true}`
- 再 `listTasks` → **`AFTER count=0`** ✅ 删除成功、任务从列表消失。

## 备注
- 本版只保证"任务从列表正确删除"；`deleteWithFiles=true` 时"连带删除磁盘文件"暂未实现（如需要我再补：删除后按 saveDir/文件名 unlink）。
- 原生版（Engine.remove）同步修好，两套界面的删除都恢复正常。
