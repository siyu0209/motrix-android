package app.motrix.android.web

import android.content.Context
import app.motrix.android.Prefs
import app.motrix.android.engine.Aria2Rpc
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 本地兼容桥 —— 通道分发器（Route 3B 核心）。
 *
 * 原版 Motrix Web UI（已构建进 assets/renderer-web）在 web target 下通过
 * `HttpWsTransport` 把每个交互打到同源 `POST /rpc/{command|query}/<channel>`，
 * 请求体 `{ "args": [...] }`。AssetServer 把请求解析后交给本类：
 *
 *   val value = dispatcher.handle(kind, channel, args)
 *   // 回写 JSON.stringify(value ?? null)
 *
 * 本类把这些渲染器通道翻译成端内 aria2 引擎（[Aria2Rpc]）的真实 JSON-RPC 调用，
 * 并把 raw aria2 status 映射成渲染器期望的 `DownloadTask` / `GlobalStats` 等结构。
 * payload 字段/类型严格对齐 `src/shared/types/task.ts` 与
 * `src/core/engine/aria2/translate.ts`。
 *
 * 设计约束：
 *  - 只依赖 org.json + 既有 [Aria2Rpc]，零第三方依赖。
 *  - 所有 rpc 调用 try/catch：query 异常返回 null，command 异常返回
 *    `{ok:false,error:{code:'engine'}}`，绝不把异常抛给 HTTP 层。
 *  - 无法映射的通道返回“空但结构合法”的值（null / [] / 默认对象 / {ok:true}），
 *    不留 TODO、不抛错。
 */
class RpcDispatcher(private val context: Context) {

    @Volatile
    private var rpc: Aria2Rpc? = null

    /** 引擎就绪/退出时挂载（null = 引擎已退出）。 */
    fun attach(rpc: Aria2Rpc?) {
        this.rpc = rpc
    }

    // 会话内设置持久化（原版设置弹窗保存后，重开能读回，不再"回滚"）
    @Volatile private var turtleMode: String = "off"
    @Volatile private var savedGeneral: JSONObject? = null
    @Volatile private var savedDownloads: JSONObject? = null
    private val appOverrides = JSONObject()

    // ─────────────────────────────────────────────────────────────
    //  入口
    // ─────────────────────────────────────────────────────────────

    /**
     * @param method  "query" 或 "command"
     * @param channel 完整通道名，如 "query:listTasks" / "command:createTask"
     * @param args    位置参数数组（org.json 解析出的 String/JSONObject/JSONArray/Number/Boolean/null）
     */
    fun handle(method: String, channel: String, args: List<Any?>): Any? {
        val name = channel.substringAfterLast(':')
        return try {
            when (name) {
                // ── queries ──────────────────────────────────────
                "listTasks" -> queryListTasks()
                "getStats" -> statsPayload()
                "getTaskDetail" -> queryGetTaskDetail(args)
                "getEngineStatus" -> queryGetEngineStatus()
                "getSettings" -> mergedAppSettings()
                "getDownloadsSettingsDraft" -> savedDownloads?.let { okValue(it) } ?: okValue(downloadsSettingsSnapshot())
                "getGeneralSettingsDraft" -> savedGeneral?.let { okValue(it) } ?: okValue(generalSettingsSnapshot())
                "getDirectoryPreferences" -> okValue(directoryPreferences())
                "getSpeedLimitState" -> speedLimitState()
                "listNotifications" -> JSONArray()
                "getUnreadNotificationCount" -> 0
                "getSpeedHistory" -> JSONArray()
                "getTransferStats" -> transferStatsSnapshot(args)
                "getTaskFiles" -> queryGetTaskFiles(args)
                // 仪表板「活动」卡：需返回 TaskActivitySnapshot（generation/revision/coverage/days）
                "getTaskActivity" -> activitySnapshotPayload(args)
                // Tracker 页（原版 UI 会一次性拉这几个，结构不对会触发错误边界）
                "getTrackerList" -> trackerListEmpty()
                "getTrackerSyncStatus" -> "idle"
                "getTrackerSources" -> JSONArray()
                "getTaskTrackerPlan" -> JSONObject()
                // 插件页
                "listPlugins" -> JSONArray()
                "listPluginGrants" -> JSONObject()
                "listRegistryPlugins" -> JSONArray()
                "getPluginConfig" -> JSONObject()
                "getPluginGrants" -> JSONObject()
                "getPluginLogState" -> JSONObject().put("entries", JSONArray()).put("verbose", false)
                "getPluginLogs" -> JSONArray()
                "getPluginHookRank" -> JSONObject()
                "checkPluginCompatibility" -> JSONObject().put("compatible", true)
                // 目录选择/记录（设置页读目录记录，缺了会显示红色"无法更新目录记录"横幅）
                "listServerDirectoryLocations" -> serverLocationsEmpty()
                "validateServerDirectory" -> {
                    val req = args.firstOrNull() as? JSONObject
                    JSONObject().put("ok", true)
                        .put("value", JSONObject().put("path", req?.optString("path") ?: defaultSaveDir()))
                }
                "listServerDirectories" -> JSONObject().put("ok", true)
                    .put("value", JSONObject().put("entries", JSONArray()).put("truncated", false).put("canCreate", false))
                // 设置>集成 页：浏览器扩展/CLI/ffmpeg 各组件的查询，缺了会整页错误边界
                "listTrusted" -> JSONArray()
                "listPaired" -> JSONArray()
                "listPendingPairRequests" -> JSONArray()
                "getStatus" -> JSONObject.NULL
                "getCliToolStatus" -> JSONObject()
                    .put("phase", "manualOnly")
                    .put("reason", "unsupported")
                    .put("capability", "unknown")
                    .put("managerOptions", JSONArray())
                    .put("packageManager", "unknown")
                    .put("nodeVersion", JSONObject.NULL)
                    .put("version", JSONObject.NULL)
                    .put("executablePath", JSONObject.NULL)
                    .put("detail", JSONObject.NULL)
                "getFfmpegInstallStatus" -> JSONObject().put("phase", "idle")
                "getFfmpegDetection" -> JSONObject.NULL

                // ── commands ─────────────────────────────────────
                "createTask" -> commandCreateTask(args)
                "parseTorrent" -> commandParseTorrent(args)
                "pauseTask" -> commandSingle(args) { g -> rpc().pause(g) }
                "resumeTask" -> commandSingle(args) { g -> rpc().unpause(g) }
                "removeTask" -> commandRemoveTask(args)
                "pauseTasks" -> commandPlural(args) { g -> rpc().pause(g) }
                "resumeTasks" -> commandPlural(args) { g -> rpc().unpause(g) }
                "removeTasks" -> commandRemoveTasks(args)
                "pauseAllTasks" -> { rpc().pauseAll(); bulkOk() }
                "resumeAllTasks" -> { rpc().unpauseAll(); bulkOk() }
                "clearStoppedTasks" -> { rpc().purgeResults(); bulkOk() }
                "updateSettings" -> commandUpdateSettings(args)
                "saveGeneralSettings" -> commandSaveGeneralSettings(args)
                "saveDownloadsSettings" -> commandSaveDownloadsSettings(args)
                "reAddTask" -> JSONObject().put("ok", true)

                // ── 未实现的通道：结构合法的降级 ───────────────────
                else -> if (method == "query") null else JSONObject().put("ok", true)
            }
        } catch (t: Throwable) {
            if (method == "query") null else engineError()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  供 WS 广播用
    // ─────────────────────────────────────────────────────────────

    /** `event:statsUpdated` 广播载荷；异常时返回 null（广播侧应跳过）。 */
    fun statsPayload(): Any? = try {
        val stat = rpc().globalStat()
        JSONObject()
            .put("totalDownloadSpeed", num(stat.optString("downloadSpeed")))
            .put("totalUploadSpeed", num(stat.optString("uploadSpeed")))
            .put("activeTasks", int(stat.optString("numActive")))
            .put("waitingTasks", int(stat.optString("numWaiting")))
            .put("stoppedTasks", int(stat.optString("numStopped")))
    } catch (t: Throwable) {
        null
    }

    /** `event:taskUpdated` / 首屏 `listTasks` 广播载荷（slim 投影）。 */
    fun tasksPayload(): Any? = try {
        queryListTasks()
    } catch (t: Throwable) {
        null
    }

    /** 原版 Trackers 页期望的 CuratedTrackerList 空结构（对齐 src/shared/types/tracker.ts）。 */
    private fun trackerListEmpty(): JSONObject = JSONObject()
        .put("effective", JSONArray())
        .put("blacklist", JSONArray())
        .put("healthMap", JSONObject())
        .put("sourceMap", JSONObject())
        .put("lastSyncAt", JSONObject.NULL)
        .put("lastProbeAt", JSONObject.NULL)

    /** 目录位置空结构（对齐 src/shared/schemas/server-directory.ts ListServerDirectoryLocationsResult）。 */
    private fun serverLocationsEmpty(): JSONObject = JSONObject()
        .put("ok", true)
        .put(
            "value", JSONObject()
                .put("common", JSONArray())
                .put("favorites", JSONArray())
                .put("recent", JSONArray())
        )

    /**
     * 仪表板「活动」卡快照（对齐 src/shared/types/task-activity.ts TaskActivitySnapshot）。
     * 端内无 SQLite 活动库，这里按请求的 days 边界返回全 0 的合法快照，避免"无法获取活动数据"。
     */
    private fun activitySnapshotPayload(args: List<Any?>): JSONObject {
        val req = args.firstOrNull() as? JSONObject
        val out = JSONObject()
        out.put("generation", "android-local")
        out.put("revision", 1)
        out.put(
            "coverage", JSONObject()
                .put("trackingStartedAt", System.currentTimeMillis())
                .put("coverageGapAt", JSONObject.NULL)
        )
        val days = JSONArray()
        val reqDays = req?.optJSONArray("days")
        if (reqDays != null) {
            for (i in 0 until reqDays.length()) {
                val d = reqDays.optJSONObject(i) ?: continue
                days.put(
                    JSONObject()
                        .put("dateKey", d.optString("dateKey"))
                        .put("submitted", 0)
                        .put("downloadCompleted", 0)
                        .put("recoveredDownloadCompleted", 0)
                )
            }
        }
        out.put("days", days)
        return out
    }

    // ─────────────────────────────────────────────────────────────
    //  查询实现
    // ─────────────────────────────────────────────────────────────

    private fun queryListTasks(): JSONArray {
        val arr = JSONArray()
        for (raw in mergedRawTasks()) arr.put(mapTask(raw, slim = true))
        return arr
    }

    private fun queryGetTaskDetail(args: List<Any?>): Any? {
        val gid = firstString(args) ?: return null
        val raw = tellStatus(gid) ?: return null
        return mapTask(raw, slim = false)
    }

    private fun queryGetTaskFiles(args: List<Any?>): Any? {
        val gid = firstString(args) ?: return JSONArray()
        val res = rpc().call("aria2.getFiles", gid)
        val rawFiles = res as? JSONArray ?: return JSONArray()
        val out = JSONArray()
        for (k in 0 until rawFiles.length()) {
            val f = rawFiles.optJSONObject(k) ?: continue
            out.put(
                JSONObject()
                    // aria2 1-based → 引擎中立 0-based
                    .put("index", int(f.optString("index")) - 1)
                    .put("path", f.optString("path"))
                    .put("size", num(f.optString("length")))
                    .put("completedBytes", num(f.optString("completedLength")))
                    .put("selected", f.optString("selected") == "true")
            )
        }
        return out
    }

    private fun queryGetEngineStatus(): JSONObject {
        val status = JSONObject()
            .put("state", if (runCatching { rpc() }.isSuccess) "ready" else "stopped")
            .put("failure", JSONObject.NULL)
            .put("managedPid", JSONObject.NULL)
        val report = try {
            val v = rpc().call("aria2.getVersion") as? JSONObject
            if (v != null) {
                val features = v.optJSONArray("enabledFeatures") ?: JSONArray()
                val hasSqlite = (0 until features.length()).any {
                    features.optString(it) == "SQLite3-Persistence"
                }
                JSONObject()
                    .put("version", v.optString("version"))
                    .put("features", features)
                    .put("hasBtSeedUnverified", false)
                    .put("hasBtSaveMetadata", false)
                    .put("hasMoveStorage", false)
                    .put("hasSqlitePersistence", hasSqlite)
            } else null
        } catch (t: Throwable) {
            null
        }
        status.put("featureReport", report ?: JSONObject.NULL)
        return status
    }

    private fun transferStatsSnapshot(args: List<Any?>): JSONObject {
        val p = args.getOrNull(0) as? JSONObject
        val dayStart = p?.optLong("dayStartMs") ?: 0L
        val dayEnd = p?.optLong("dayEndMs") ?: 0L
        val zeroRange = JSONObject()
            .put("downloadBytes", "0")
            .put("uploadBytes", "0")
            .put("totalBytes", "0")
            .put("startedAt", dayStart)
            .put("coverageStartedAt", dayStart)
        val today = JSONObject(zeroRange.toString()).put("endsAt", dayEnd)
        return JSONObject()
            .put("today", today)
            .put("allTime", zeroRange)
            .put("updatedAt", JSONObject.NULL)
            .put("accuracy", "estimated")
    }

    // ─────────────────────────────────────────────────────────────
    //  命令实现
    // ─────────────────────────────────────────────────────────────

    private fun commandCreateTask(args: List<Any?>): Any? {
        val req = args.getOrNull(0) as? JSONObject ?: return invalidSource("missing request")
        return try {
            when (req.optString("type")) {
                "http" -> createHttpTask(req)
                "bt" -> createBtTask(req)
                else -> invalidSource("unsupported type")
            }
        } catch (t: Throwable) {
            engineError()
        }
    }

    private fun createHttpTask(req: JSONObject): JSONObject {
        val uris = req.optJSONArray("uris") ?: JSONArray()
        if (uris.length() == 0) return invalidSource("empty uris")
        val opts = JSONObject()
        putOptString(opts, "dir", req.optString("saveDir"))
        putOptString(opts, "out", req.optString("filename"))
        if (req.has("connections") && !req.isNull("connections")) {
            val c = req.optInt("connections")
            if (c > 0) {
                opts.put("split", c.toString())
                opts.put("max-connection-per-server", c.toString())
            }
        }
        val headers = req.optJSONArray("headers")
        if (headers != null && headers.length() > 0) {
            val h = JSONArray()
            for (k in 0 until headers.length()) {
                val item = headers.optJSONObject(k) ?: continue
                val n = item.optString("name")
                if (n.isNotEmpty()) h.put("$n: ${item.optString("value")}")
            }
            if (h.length() > 0) opts.put("header", h)
        }
        putOptString(opts, "all-proxy", req.optString("proxy"))
        val gid = rpc().addUri(uris, opts)
        return created(gid)
    }

    private fun createBtTask(req: JSONObject): JSONObject {
        val payload = req.optJSONObject("payload") ?: return invalidSource("missing payload")
        val opts = JSONObject()
        putOptString(opts, "dir", req.optString("saveDir"))
        val selected = req.optJSONArray("selectedFiles")
        if (selected != null && selected.length() > 0) {
            val parts = ArrayList<String>()
            for (k in 0 until selected.length()) parts.add((selected.optInt(k) + 1).toString())
            opts.put("select-file", parts.joinToString(","))
        }
        if (req.has("dlLimit") && !req.isNull("dlLimit")) {
            opts.put("max-download-limit", req.optLong("dlLimit").toString())
        }
        if (req.has("ulLimit") && !req.isNull("ulLimit")) {
            opts.put("max-upload-limit", req.optLong("ulLimit").toString())
        }
        if (req.has("seedRatio") && !req.isNull("seedRatio")) {
            opts.put("seed-ratio", req.optDouble("seedRatio").toString())
        }
        val gid = when (payload.optString("kind")) {
            "magnet" -> {
                val magnet = payload.optString("uri")
                if (magnet.isEmpty()) return invalidSource("empty magnet")
                rpc().addUri(JSONArray().put(magnet), opts)
            }
            "torrent-base64" -> {
                val b64 = payload.optString("base64")
                if (b64.isEmpty()) return invalidSource("empty torrent")
                rpc().addTorrent(b64, opts)
            }
            else -> return invalidSource("unsupported payload kind")
        }
        return created(gid)
    }

    private fun commandParseTorrent(args: List<Any?>): Any? {
        // 端侧无 torrent 解析器：返回结构合法但内容空的 torrentMeta，
        // 避免 add-task 表单在校验阶段崩溃（文件列表为空，用户可改用磁力/直链）。
        val zeroHash = "0".repeat(40)
        return JSONObject()
            .put("name", "torrent")
            .put("infoHash", zeroHash)
            .put("totalSize", 0)
            .put("files", JSONArray())
    }

    private inline fun commandSingle(args: List<Any?>, action: (String) -> Unit): JSONObject {
        val gid = firstString(args) ?: return JSONObject().put("ok", false)
            .put("error", JSONObject().put("code", "invalidTaskId"))
        action(gid)
        return JSONObject().put("ok", true)
    }

    private inline fun commandPlural(args: List<Any?>, action: (String) -> Unit): JSONObject {
        val ids = asStringList(args.getOrNull(0))
        val succeeded = JSONArray()
        val failed = JSONArray()
        for (id in ids) {
            try {
                action(id)
                succeeded.put(id)
            } catch (t: Throwable) {
                failed.put(JSONObject().put("taskId", id).put("reason", t.message ?: "engine"))
            }
        }
        return JSONObject().put("succeeded", succeeded).put("failed", failed)
    }

    private fun commandRemoveTask(args: List<Any?>): JSONObject {
        val obj = args.getOrNull(0) as? JSONObject
        val gid = obj?.optString("taskId")?.ifEmpty { null } ?: firstString(args)
        if (gid == null) return JSONObject().put("ok", false)
            .put("error", JSONObject().put("code", "invalidTaskId"))
        removeOne(gid)
        return JSONObject().put("ok", true)
    }

    private fun commandRemoveTasks(args: List<Any?>): JSONObject {
        val obj = args.getOrNull(0) as? JSONObject
        val ids = obj?.optJSONArray("taskIds")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
        } ?: asStringList(args.getOrNull(0))
        val succeeded = JSONArray()
        val failed = JSONArray()
        for (id in ids) {
            try {
                removeOne(id)
                succeeded.put(id)
            } catch (t: Throwable) {
                failed.put(JSONObject().put("taskId", id).put("reason", t.message ?: "engine"))
            }
        }
        return JSONObject().put("succeeded", succeeded).put("failed", failed)
    }

    private fun removeOne(gid: String) {
        // 已完成/错误/暂停任务无法用 remove/forceRemove 移除（aria2 会报 "Cannot remove"），
        // 只能靠 removeDownloadResult 从列表清除；活动任务两步都试，任何单步失败都忽略，
        // 只要 removeDownloadResult 成功任务就从 tellStopped 列表消失。修复"删除任务失灵"。
        try { rpc().remove(gid) } catch (_: Throwable) {}
        try { rpc().removeResult(gid) } catch (_: Throwable) {}
    }

    private fun commandUpdateSettings(args: List<Any?>): JSONObject {
        val patch = args.getOrNull(0) as? JSONObject ?: return JSONObject().put("ok", true)
        val sp = patch.optJSONObject("speedLimit")
        sp?.optString("turtle")?.takeIf { it.isNotEmpty() }?.let { turtleMode = it }
        applySpeedLimitsFrom(sp, patch.optJSONObject("engine"))
        synchronized(appOverrides) { deepMerge(appOverrides, patch) }
        return JSONObject().put("ok", true)
    }

    private fun commandSaveDownloadsSettings(args: List<Any?>): JSONObject {
        val req = args.getOrNull(0) as? JSONObject
        if (req != null) {
            val settings = req.optJSONObject("settings") ?: req
            applySpeedLimitsFrom(settings.optJSONObject("speedLimit"), settings.optJSONObject("engine"))
            savedDownloads = JSONObject()
                .put("revision", req.optString("revision").ifEmpty { uuid() })
                .put("settings", settings)
                .put("directoryPreferences", req.optJSONObject("directoryPreferences") ?: directoryPreferences())
        }
        return okValue(savedDownloads ?: downloadsSettingsSnapshot())
    }

    private fun commandSaveGeneralSettings(args: List<Any?>): JSONObject {
        val req = args.getOrNull(0) as? JSONObject
        if (req != null) {
            val appObj = req.optJSONObject("app")
                ?: req.optJSONObject("settings")?.optJSONObject("app")
            savedGeneral = JSONObject()
                .put("revision", req.optString("revision").ifEmpty { uuid() })
                .put("app", appObj ?: generalSettingsSnapshot().getJSONObject("app"))
                .put("directoryPreferences", req.optJSONObject("directoryPreferences") ?: directoryPreferences())
        }
        return okValue(savedGeneral ?: generalSettingsSnapshot())
    }

    /** getSettings 结果：默认全量 + 会话内已保存/已修改的覆盖，保证设置读回一致。 */
    private fun mergedAppSettings(): JSONObject {
        val base = defaultAppSettings()
        synchronized(appOverrides) { deepMerge(base, appOverrides) }
        savedGeneral?.optJSONObject("app")?.let { deepMerge(base.getJSONObject("app"), it) }
        savedDownloads?.optJSONObject("settings")?.let { s ->
            s.optJSONObject("app")?.let { deepMerge(base.getJSONObject("app"), it) }
            s.optJSONObject("engine")?.let { deepMerge(base.getJSONObject("engine"), it) }
        }
        return base
    }

    /** 深合并：同名字段递归合并 JSONObject，其它直接覆盖。 */
    private fun deepMerge(dst: JSONObject, src: JSONObject) {
        for (k in src.keys()) {
            val sv = src.get(k)
            val dv = dst.opt(k)
            if (sv is JSONObject && dv is JSONObject) deepMerge(dv, sv) else dst.put(k, sv)
        }
    }

    /** 把渲染器的限速/并发设置翻译成 aria2 changeGlobalOption。 */
    private fun applySpeedLimitsFrom(speedLimit: JSONObject?, engine: JSONObject?) {
        val opts = JSONObject()
        val turtle = speedLimit?.optString("turtle")
        if (!turtle.isNullOrEmpty()) {
            when (turtle) {
                "on" -> {
                    opts.put("max-overall-download-limit", (512L * 1024).toString())
                    opts.put("max-overall-upload-limit", (64L * 1024).toString())
                }
                "off" -> {
                    opts.put("max-overall-download-limit", "0")
                    opts.put("max-overall-upload-limit", "0")
                }
                else -> {}
            }
        } else {
            val base = speedLimit?.optJSONObject("base")
            if (base != null && base.has("download")) {
                opts.put("max-overall-download-limit", base.optLong("download").toString())
            }
            if (base != null && base.has("upload")) {
                opts.put("max-overall-upload-limit", base.optLong("upload").toString())
            }
        }
        if (engine != null && engine.has("maxConcurrentDownloads")) {
            opts.put("max-concurrent-downloads", engine.optInt("maxConcurrentDownloads").toString())
        }
        if (opts.length() > 0) rpc().changeGlobalOption(opts)
    }

    // ─────────────────────────────────────────────────────────────
    //  aria2 raw → DownloadTask 映射（对齐 translate.ts）
    // ─────────────────────────────────────────────────────────────

    private fun mergedRawTasks(): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        appendAll(out, safeArray { rpc().tellActive() })
        appendAll(out, safeArray { rpc().tellWaiting() })
        appendAll(out, safeArray { rpc().tellStopped() })
        return out
    }

    private inline fun safeArray(block: () -> JSONArray): JSONArray =
        try { block() } catch (t: Throwable) { JSONArray() }

    private fun appendAll(dst: MutableList<JSONObject>, src: JSONArray) {
        for (k in 0 until src.length()) src.optJSONObject(k)?.let { dst.add(it) }
    }

    private fun tellStatus(gid: String): JSONObject? = try {
        rpc().call("aria2.tellStatus", gid) as? JSONObject
    } catch (t: Throwable) {
        // 找不到时回退到列表里扫描
        mergedRawTasks().firstOrNull { it.optString("gid") == gid }
    }

    private fun mapTask(raw: JSONObject, slim: Boolean): JSONObject {
        val gid = raw.optString("gid")
        val rawStatus = raw.optString("status")
        val bt = raw.optJSONObject("bittorrent")
        val files = raw.optJSONArray("files")
        val totalLength = raw.optString("totalLength", "0").toLongOrNull() ?: 0L
        val completedLength = raw.optString("completedLength", "0").toLongOrNull() ?: 0L
        val uploadLength = raw.optString("uploadLength", "0").toLongOrNull() ?: 0L
        val downloadSpeed = raw.optString("downloadSpeed", "0").toLongOrNull() ?: 0L
        val uploadSpeed = raw.optString("uploadSpeed", "0").toLongOrNull() ?: 0L
        val connections = raw.optString("connections", "0").toIntOrNull() ?: 0
        val numSeeders = raw.optString("numSeeders", "0").toIntOrNull() ?: 0
        val now = System.currentTimeMillis()

        val status = translateStatus(raw, rawStatus, bt)
        val type = detectTaskType(raw, bt, files)
        val kind = if (type == "bt" || type == "magnet") "bt" else "direct"
        val uris = extractUris(raw, files)
        val name = extractName(raw, bt, files, gid)
        val displayName = toFinalPath(name)

        val diskPath = if (kind == "bt") {
            raw.optString("dir")
        } else {
            val fp = firstFile(files)?.optString("path").orEmpty()
            if (fp.isNotEmpty()) fp
            else {
                val dir = raw.optString("dir")
                if (dir.isNotEmpty()) "$dir/$displayName" else ""
            }
        }
        val finalPath = toFinalPath(diskPath)
        val finalName = baseName(finalPath)

        val out = JSONObject()
            .put("id", gid)
            .put("engineTaskId", gid)
            .put("name", displayName)
            .put("kind", kind)
            .put("type", type)
            .put("status", status)
            .put("progress", if (totalLength > 0L) completedLength.toDouble() / totalLength else 0.0)
            .put("totalBytes", totalLength)
            .put("downloadedBytes", completedLength)
            .put("downloadSpeed", downloadSpeed)
            .put("uploadSpeed", uploadSpeed)
            .put("etaSeconds", computeEta(totalLength, completedLength, downloadSpeed))
            .put("saveDir", raw.optString("dir"))
        // terminal 字段
        putOrNull(out, "finishedAt", if (rawStatus == "complete" || rawStatus == "error") now else null)
        putOrNull(out, "errorMessage", raw.optString("errorMessage").ifEmpty { null })
        putOrNull(out, "errorCode", if (status == "error" || status == "removed") translateErrorCode(raw.optString("errorCode")) else null)
        putOrNull(out, "errorDetailKey", null)
        putOrNull(out, "errorDetailParams", null)
        out.put("diagnosisRevision", 0)
            .put("metadataProgress", 0)
            .put("uris", uris)
            .put("uploadedBytes", uploadLength)
            .put("uploadedBytesBaseline", 0)
            .put("fileCount", files?.length() ?: 0)
            .put("connections", connections)
            .put("pieceLength", raw.optString("pieceLength", "0").toLongOrNull() ?: 0L)
        putOrNull(out, "infoHash", raw.optString("infoHash").ifEmpty { null })
        out.put("priority", 0)
        putOrNull(out, "category", null)
        out.put("dlLimit", 0L)
            .put("ulLimit", 0L)
            .put("filename", displayName)
            .put("sizeWhenDone", totalLength)
        putOrNull(out, "bt", if (bt != null) translateBtExtension(raw, bt, files, connections, numSeeders, uploadLength, totalLength, slim) else null)
        out.put("diskPath", diskPath)
            .put("finalPath", finalPath)
            .put("finalName", finalName)
            .put("transitionPhase", "idle")
        putOrNull(out, "torrentMetaPath", null)
        out.put("source", "user")
        putOrNull(out, "sourceMeta", null)

        // instances[]
        val progressInt = if (totalLength > 0L)
            ((completedLength * 100L) / totalLength).toInt().coerceIn(0, 100) else 0
        val instance = JSONObject()
            .put("instanceId", "adopted:$gid")
            .put("motrixId", "")
        putOrNull(instance, "gid", gid)
        instance.put("phase", if (kind == "bt") "bt_download" else "http_download")
            .put("status", status)
            .put("progress", progressInt)
            .put("totalBytes", totalLength)
            .put("downloadedBytes", completedLength)
            .put("uploadedBytes", uploadLength)
            .put("diskPath", diskPath)
            .put("transitionPhase", "idle")
            .put("uris", uris)
        putOrNull(instance, "uriHash", null)
        instance.put("payload", JSONObject())
            .put("createdAt", now)
            .put("updatedAt", now)
        out.put("instances", JSONArray().put(instance))
        return out
    }

    private fun translateStatus(raw: JSONObject, rawStatus: String, bt: JSONObject?): String {
        if (rawStatus == "active" && raw.optString("seeder") == "true" && bt != null) return "seeding"
        if (rawStatus == "active" && raw.optString("totalLength") == "0" && bt != null) return "fetching_metadata"
        return when (rawStatus) {
            "active" -> "downloading"
            "waiting" -> "queued"
            "paused" -> "paused"
            "complete" -> "completed"
            "error" -> "error"
            "removed" -> "removed"
            else -> "error"
        }
    }

    private fun detectTaskType(raw: JSONObject, bt: JSONObject?, files: JSONArray?): String {
        val firstUri = firstFile(files)?.optJSONArray("uris")?.optJSONObject(0)?.optString("uri").orEmpty()
        if (firstUri.startsWith("magnet:")) return "magnet"
        if (firstUri.startsWith("ftp://")) return "ftp"
        if (bt != null) return "bt"
        return "http"
    }

    private fun extractName(raw: JSONObject, bt: JSONObject?, files: JSONArray?, gid: String): String {
        val btName = bt?.optJSONObject("info")?.optString("name").orEmpty()
        if (btName.isNotEmpty()) return btName
        val fp = firstFile(files)?.optString("path").orEmpty()
        if (fp.isNotEmpty()) return baseName(fp)
        val uri = firstFile(files)?.optJSONArray("uris")?.optJSONObject(0)?.optString("uri").orEmpty()
        if (uri.isNotEmpty()) {
            val pathPart = uri.substringAfter("://", uri).substringAfter('/', "")
            if (pathPart.isNotEmpty()) return baseName(pathPart)
        }
        return gid
    }

    private fun extractUris(raw: JSONObject, files: JSONArray?): JSONArray {
        val out = JSONArray()
        val seen = LinkedHashSet<String>()
        if (files != null) {
            for (k in 0 until files.length()) {
                val f = files.optJSONObject(k) ?: continue
                val us = f.optJSONArray("uris") ?: continue
                for (j in 0 until us.length()) {
                    val u = us.optJSONObject(j)?.optString("uri").orEmpty()
                    if (u.isNotEmpty() && seen.add(u)) out.put(u)
                }
            }
        }
        return out
    }

    private fun translateBtExtension(
        raw: JSONObject,
        bt: JSONObject,
        files: JSONArray?,
        connections: Int,
        numSeeders: Int,
        uploadLength: Long,
        totalLength: Long,
        slim: Boolean
    ): JSONObject {
        val announce = bt.optJSONArray("announceList")
        val trackers = JSONArray()
        if (!slim && announce != null) {
            for (k in 0 until announce.length()) {
                val inner = announce.optJSONArray(k) ?: continue
                for (j in 0 until inner.length()) trackers.put(inner.optString(j))
            }
        }
        val selected = JSONArray()
        if (files != null) {
            for (k in 0 until files.length()) {
                val f = files.optJSONObject(k) ?: continue
                if (f.optString("selected") == "true") selected.put((f.optString("index", "0").toIntOrNull() ?: 0) - 1)
            }
        }
        val ratio = if (totalLength > 0L) uploadLength.toDouble() / totalLength else 0.0
        val obj = JSONObject()
            .put("peers", connections)
            .put("seeds", numSeeders)
            .put("ratio", ratio)
            .put("trackers", trackers)
            .put("selectedFiles", selected)
            .put("peersInSwarm", 0)
            .put("seedsInSwarm", 0)
            .put("announceList", if (slim) JSONArray() else (announce ?: JSONArray()))
        putOrNull(obj, "comment", bt.optString("comment").ifEmpty { null })
        obj.put("isPrivate", false)
        putOrNull(obj, "magnetUri", null)
        obj.put("sequentialDownload", false)
        return obj
    }

    private fun translateErrorCode(rawCode: String): String? {
        val trimmed = rawCode.trim()
        if (trimmed.isEmpty() || trimmed == "0") return null
        val code = trimmed.toIntOrNull() ?: return "DL_UNKNOWN"
        return when (code) {
            2, 5 -> "DL_TIMEOUT"
            3, 4 -> "DL_NOT_FOUND"
            6, 19 -> "DL_NETWORK_ERROR"
            9 -> "DL_DISK_FULL"
            11, 13, 14, 15, 16, 17, 18 -> "DL_FILE_WRITE_ERROR"
            23 -> "DL_TOO_MANY_REDIRECTS"
            24 -> "DL_UNAUTHORIZED"
            8, 21, 22, 29 -> "DL_SERVER_ERROR"
            25, 26, 27 -> "DL_BT_METADATA_FAILED"
            10, 32 -> "DL_CHECKSUM_MISMATCH"
            else -> "DL_UNKNOWN"
        }
    }

    private fun computeEta(total: Long, completed: Long, speed: Long): Long {
        val remaining = total - completed
        if (speed <= 0L || remaining <= 0L) return 0L
        return Math.round(remaining.toDouble() / speed)
    }

    // ─────────────────────────────────────────────────────────────
    //  设置/统计的默认骨架（端侧无 settings 子系统时的合法结构）
    // ─────────────────────────────────────────────────────────────

    private fun uuid(): String = UUID.randomUUID().toString()

    private fun defaultSaveDir(): String =
        (context.getExternalFilesDir(null) ?: context.filesDir)?.absolutePath ?: "/storage/emulated/0/Download"

    private fun directoryPreferences(): JSONObject =
        JSONObject().put("favorites", JSONArray()).put("recent", JSONArray())

    private fun speedLimitSettings(): JSONObject = JSONObject()
        .put("base", JSONObject().put("download", 0).put("upload", 0))
        .put("alt", JSONObject().put("download", 512 * 1024).put("upload", 64 * 1024))
        .put("turtle", turtleMode)
        .put(
            "auto", JSONObject()
                .put(
                    "schedule",
                    JSONObject().put("enabled", false).put("from", "23:00").put("to", "07:00")
                        .put("days", JSONArray())
                )
                .put("videoApp", JSONObject().put("enabled", false).put("processNames", JSONArray()))
                .put(
                    "adaptive", JSONObject()
                        .put("enabled", false).put("linkDown", 0).put("linkUp", 0)
                        .put("headroomPercent", 80)
                        .put(
                            "speedTest", JSONObject()
                                .put("providers", JSONArray())
                                .put("selectedProviderId", "cloudflare")
                                .put("concurrency", 4)
                                .put("maxDurationSec", 10)
                                .put("maxDataMB", 1024)
                        )
                )
        )

    private fun speedLimitState(): JSONObject {
        val dl = if (turtleMode == "on") 512L * 1024 else 0L
        val ul = if (turtleMode == "on") 64L * 1024 else 0L
        return JSONObject()
            .put("turtle", turtleMode)
            .put("effective", JSONObject().put("download", dl).put("upload", ul))
            .put("activeReason", "none")
    }

    private fun downloadsSettingsSnapshot(): JSONObject = JSONObject()
        .put("revision", uuid())
        .put(
            "settings", JSONObject()
                .put(
                    "app", JSONObject()
                        .put("defaultSaveDir", defaultSaveDir())
                        .put("autofillClipboardLinks", false)
                        .put("fileDeletionMode", "permanent")
                        .put("speedLimitUnits", JSONObject()
                            .put("baseUpload", "kilo").put("baseDownload", "kilo")
                            .put("altUpload", "kilo").put("altDownload", "kilo"))
                )
                .put(
                    "engine", JSONObject()
                        .put("performanceProfile", "balanced")
                        .put("maxConcurrentDownloads", 5)
                        .put("maxConnectionPerServer", 16)
                        .put("split", 16)
                        .put("minSplitSize", 1048576)
                        .put("userAgent", "Motrix/2.x")
                        .put("connectTimeout", 10)
                        .put("socketTimeout", 60)
                        .put("maxTries", 5)
                        .put("retryWait", 0)
                        .put("lowestSpeedLimit", 0)
                        .put("fileAllocation", "none")
                        .put("remoteTime", true)
                        .put("diskCache", 0)
                )
                .put("speedLimit", speedLimitSettings())
        )
        .put("directoryPreferences", directoryPreferences())

    private fun generalSettingsSnapshot(): JSONObject = JSONObject()
        .put("revision", uuid())
        .put(
            "app", JSONObject()
                .put("runMode", 1)
                .put("lightweightMode", false)
                .put("launchAtStartup", false)
                .put("showMainWindowAtLogin", false)
                .put("defaultSaveDir", defaultSaveDir())
                .put("notifyOnComplete", true)
                .put("notifyOnError", true)
                .put("notifyInAppOnComplete", true)
                .put("notifyInAppOnError", true)
                .put("notificationBadgeStyle", "dot")
                .put("autofillClipboardLinks", false)
                .put("warnBeforeQuit", true)
        )
        .put("directoryPreferences", directoryPreferences())

    private fun defaultAppSettings(): JSONObject = JSONObject()
        .put("version", 1)
        .put(
            "engine", JSONObject()
                .put("rpcPort", 16800)
                .put("rpcSecret", rpc?.secret ?: "motrix")
                .put("listenPort", 0)
                .put("dhtListenPort", 6881)
                .put("dhtEnabled", true)
                .put("performanceProfile", "balanced")
                .put("maxConcurrentDownloads", 5)
                .put("maxConnectionPerServer", 16)
                .put("split", 16)
                .put("minSplitSize", 1048576)
                .put("userAgent", "Motrix/2.x")
                .put("connectTimeout", 10)
                .put("socketTimeout", 60)
                .put("maxTries", 5)
                .put("retryWait", 0)
                .put("lowestSpeedLimit", 0)
                .put("dnsMode", "auto")
                .put("btMaxPeers", 60)
                .put("btEnableLpd", true)
                .put("seedRatio", 0.0)
                .put("seedTime", 0)
                .put("fileAllocation", "none")
                .put("remoteTime", true)
                .put("diskCache", 0)
                .put("sessionSaveInterval", 30)
                .put("sqlite3Persistence", false)
                .put("sqlite3DbPath", "")
                .put("sqlite3HistoryLimit", 0)
                .put("magnetResolveTimeout", 30000)
        )
        .put(
            "app", JSONObject()
                .put("launchAtStartup", false)
                .put("showMainWindowAtLogin", false)
                .put("mainWindowMinWidth", 0)
                .put("mainWindowMinHeight", 0)
                .put("theme", "system")
                .put("sidebarColor", "default")
                .put("reduceMotion", false)
                .put("hardwareAcceleration", true)
                .put("byteUnitSystem", "binary")
                .put("speedLimitUnits", JSONObject()
                    .put("baseUpload", "kilo").put("baseDownload", "kilo")
                    .put("altUpload", "kilo").put("altDownload", "kilo"))
                .put("language", "system")
                .put("directoryPreferences", directoryPreferences())
                .put("defaultSaveDir", defaultSaveDir())
                .put("fileDeletionMode", "permanent")
                .put("notifyOnComplete", true)
                .put("notifyOnError", true)
                .put("notifyInAppOnComplete", true)
                .put("notifyInAppOnError", true)
                .put("notificationBadgeStyle", "dot")
                .put("autofillClipboardLinks", false)
                .put("protocols", JSONObject().put("magnet", true))
                .put("runMode", 1)
                .put("lightweightMode", false)
                .put("showDownloadProgress", false)
                .put("keepStatsBarVisible", false)
                .put("traySpeedometer", false)
                .put("trayIconColor", "auto")
                .put("magnetFileSelection", true)
                .put("magnetFileSelectionAutoDownload", false)
                .put("magnetFileSelectionTimeoutSeconds", 60)
                .put("browserBridgeEnabled", true)
                .put("liquidGlassEffect", false)
                .put("warnBeforeQuit", true)
                .put("checkForUpdatesOnLaunch", false)
                .put("updateChannel", "stable")
        )
        .put("onboarding", JSONObject().put("disclaimerAccepted", true))
        .put(
            "nat", JSONObject()
                .put("enabled", false)
                .put("preferredProtocol", "auto")
                .put("mappingTtl", 0)
                .put("natTypeDetectionEnabled", false)
                .put("stunServers", JSONArray())
                .put("portReachabilityCheckEnabled", false)
                .put("portCheckerEndpoints", JSONArray())
                .put("autoDiagnostic", false)
                .put("diagnosticIntervalSec", 0)
        )
        .put(
            "proxy", JSONObject()
                .put("enabled", false)
                .put("protocol", "http")
                .put("host", "")
                .put("port", 0)
                .put("user", "")
                .put("bypass", JSONArray())
                .put(
                    "scopes", JSONObject()
                        .put("download", false)
                        .put("updateApp", false)
                        .put("updateTrackers", false)
                        .put("plugins", false)
                )
        )
        .put("plugins", JSONObject())
        .put(
            "tracker", JSONObject()
                .put("autoSync", false)
                .put("syncIntervalHours", 0)
                .put("sourcesEnabled", false)
                .put("sources", JSONArray())
                .put("probeEnabled", false)
                .put("probeTimeoutMs", 0)
                .put("healthyThresholdMs", 0)
                .put("minSuccessRate", 0)
                .put("maxTrackerCount", 0)
                .put("blacklistEnabled", false)
                .put("blacklistSources", JSONArray())
        )
        .put("geoip", JSONObject().put("enabled", false))
        .put(
            "media", JSONObject()
                .put("ffmpegBinaryPath", "")
                .put("ffmpegStagingMB", 0)
                .put("ffmpegOpTimeoutSec", 0)
        )
        .put("dashboard", JSONObject().put("version", 1).put("columns", 4).put("tiles", JSONArray()))
        .put("speedLimit", speedLimitSettings())
        .put("bridge", JSONObject())
        .put("windowState", JSONObject())
        .put("resolvedLanguage", "zh-CN")

    // ─────────────────────────────────────────────────────────────
    //  小工具
    // ─────────────────────────────────────────────────────────────

    private fun rpc(): Aria2Rpc {
        rpc?.let { return it }
        // 惰性自愈：若引擎已起但未显式 attach（例如 3B 页面 attach 轮询未命中），
        // 直接用本地 Prefs 里的 rpc 端口/密钥试探一次，成功即挂载。
        val s = Prefs(context).loadSettings()
        val candidate = Aria2Rpc(s.rpcPort, s.rpcSecret)
        if (candidate.versionOk()) {
            rpc = candidate
            return candidate
        }
        throw IllegalStateException("aria2 rpc not attached")
    }

    private fun engineError(): JSONObject = JSONObject()
        .put("ok", false)
        .put("error", JSONObject().put("code", "engine"))

    private fun bulkOk(): JSONObject =
        JSONObject().put("succeeded", JSONArray()).put("failed", JSONArray())

    private fun created(gid: String?): JSONObject {
        val id = gid.orEmpty()
        return JSONObject()
            .put("outcome", "created")
            .put("gid", id)
            .put("taskId", id)
    }

    private fun invalidSource(msg: String): JSONObject = JSONObject()
        .put("outcome", "invalid-source")
        .put("failure", JSONObject().put("code", "engine").put("message", msg))

    private fun okValue(value: Any?): JSONObject =
        JSONObject().put("ok", true).put("value", value ?: JSONObject.NULL)

    private fun putOrNull(o: JSONObject, key: String, value: Any?) {
        if (value == null) o.put(key, JSONObject.NULL) else o.put(key, value)
    }

    private fun putOptString(o: JSONObject, key: String, value: String?) {
        if (!value.isNullOrEmpty()) o.put(key, value)
    }

    private fun firstString(args: List<Any?>): String? {
        val v = args.getOrNull(0) ?: return null
        val s = when (v) {
            is String -> v
            is JSONObject -> v.optString("taskId")
            else -> v.toString()
        }
        return s.ifEmpty { null }
    }

    private fun asStringList(v: Any?): List<String> {
        val out = ArrayList<String>()
        when (v) {
            is JSONArray -> for (k in 0 until v.length()) {
                val e = v.opt(k)
                if (e != null && e != JSONObject.NULL) out.add(e.toString())
            }
            is List<*> -> for (e in v) if (e != null) out.add(e.toString())
            is String -> if (v.isNotEmpty()) out.add(v)
            is JSONObject -> v.optString("taskId").let { if (it.isNotEmpty()) out.add(it) }
        }
        return out
    }

    private fun firstFile(files: JSONArray?): JSONObject? =
        if (files != null && files.length() > 0) files.optJSONObject(0) else null

    private fun baseName(p: String): String {
        if (p.isEmpty()) return ""
        val norm = p.replace('\\', '/')
        val idx = norm.lastIndexOf('/')
        return if (idx >= 0) norm.substring(idx + 1) else norm
    }

    private fun toFinalPath(p: String): String =
        if (p.endsWith(INCOMPLETE_SUFFIX)) p.substring(0, p.length - INCOMPLETE_SUFFIX.length) else p

    private fun num(s: String): Long = s.trim().toLongOrNull() ?: 0L
    private fun int(s: String): Int = s.trim().toIntOrNull() ?: 0

    private companion object {
        const val INCOMPLETE_SUFFIX = ".motrix"
    }
}
