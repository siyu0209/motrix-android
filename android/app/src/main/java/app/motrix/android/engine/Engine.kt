package app.motrix.android.engine

import android.content.Context
import app.motrix.android.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 单例下载引擎：封装 aria2 进程 + RPC + 轮询，向上暴露 StateFlow。
 */
object Engine {

    private var appContext: Context? = null
    private var proc: Aria2Process? = null
    private var rpc: Aria2Rpc? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    /** 用户重命名映射：原绝对路径 -> 新绝对路径（aria2 不支持改文件名，这里本地持久化覆盖显示/打开）。 */
    private val renames = HashMap<String, String>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    private val _stat = MutableStateFlow(GlobalStat())
    val stat: StateFlow<GlobalStat> = _stat

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun init(ctx: Context) {
        if (appContext == null) {
            appContext = ctx.applicationContext
            proc = Aria2Process(ctx.applicationContext)
            loadRenames()
        }
    }

    fun isRunning(): Boolean = _running.value

    suspend fun start(settings: EngineSettings): Boolean = withContext(Dispatchers.IO) {
        if (_running.value) return@withContext true
        _error.value = null
        try {
            val p = proc ?: run { _error.value = "引擎未初始化"; return@withContext false }
            p.start(settings)
            val r = Aria2Rpc(settings.rpcPort, settings.rpcSecret)
            rpc = r
            var ok = false
            for (i in 0 until 60) {
                if (r.versionOk()) { ok = true; break }
                if (i >= 8 && !p.isRunning()) break   // 进程已退出，无需再等满 12s
                delay(200)
            }
            if (!ok) {
                val diag = buildString {
                    append("aria2 启动失败（进程未就绪）\n")
                    append("二进制: ").append(p.binaryPath ?: "?").append('\n')
                    append("进程存活: ").append(p.isRunning()).append("   退出码: ").append(p.lastExitCode ?: "未知/仍在运行").append('\n')
                    append("最后输出: ").append(p.lastLogLine.ifBlank { "(无)" }).append('\n')
                    append("版本自检: ").append(p.probeVersion()).append('\n')
                    append("日志: ").append(File(appContext!!.filesDir, "aria2.log").absolutePath)
                }
                _error.value = diag
                p.stop()
                return@withContext false
            }
            _running.value = true
            startPolling()
            true
        } catch (e: Exception) {
            _error.value = "启动失败：${e.javaClass.simpleName}: ${e.message}"
            false
        }
    }

    fun stop() {
        pollJob?.cancel(); pollJob = null
        try { rpc?.shutdown() } catch (_: Exception) {}
        proc?.stop()
        _running.value = false
        _tasks.value = emptyList()
        _stat.value = GlobalStat()
    }

    // 重启引擎（保存高级设置后立即生效；会中断当前下载队列）
    fun restart(onDone: (() -> Unit)? = null) = scope.launch {
        try { stop() } catch (_: Exception) {}
        delay(400)
        val ctx = appContext
        if (ctx != null) { try { start(Prefs(ctx).loadSettings()) } catch (_: Exception) {} }
        withContext(Dispatchers.Main) { onDone?.invoke() }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && _running.value) {
                try { refresh() } catch (_: Exception) {}
                delay(1000)
            }
        }
    }

    private fun refresh() {
        val r = rpc ?: return
        val list = ArrayList<DownloadTask>()
        r.tellActive().forEachObj { list.add(it.toTask()) }
        r.tellWaiting().forEachObj { list.add(it.toTask()) }
        r.tellStopped().forEachObj { list.add(it.toTask()) }
        _tasks.value = list.map { applyRename(it) }
        val gs = r.globalStat()
        _stat.value = GlobalStat(
            downloadSpeed = gs.optString("downloadSpeed", "0").toLongOrNull() ?: 0L,
            uploadSpeed = gs.optString("uploadSpeed", "0").toLongOrNull() ?: 0L,
            numActive = gs.optString("numActive", "0").toIntOrNull() ?: 0,
            numWaiting = gs.optString("numWaiting", "0").toIntOrNull() ?: 0,
            numStopped = gs.optString("numStopped", "0").toIntOrNull() ?: 0
        )
    }

    private inline fun JSONArray.forEachObj(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) optJSONObject(i)?.let(block)
    }

    private fun JSONObject.toTask(): DownloadTask {
        val f0 = optJSONArray("files")?.optJSONObject(0)
        val path = f0?.optString("path", "") ?: ""
        val baseName = File(path).name
        val btObj = optJSONObject("bittorrent")
        val btName = btObj?.optJSONObject("info")?.optString("name", "")
        val name = when {
            !btName.isNullOrBlank() -> btName
            baseName.isNotBlank() -> baseName
            else -> optString("gid")
        }
        return DownloadTask(
            gid = optString("gid"),
            name = name,
            status = optString("status", "unknown"),
            totalBytes = optString("totalLength", "0").toLongOrNull() ?: 0L,
            doneBytes = optString("completedLength", "0").toLongOrNull() ?: 0L,
            downloadSpeed = optString("downloadSpeed", "0").toLongOrNull() ?: 0L,
            uploadSpeed = optString("uploadSpeed", "0").toLongOrNull() ?: 0L,
            dir = optString("dir", ""),
            path = path,
            errorMessage = optString("errorMessage", ""),
            connections = optString("connections", "0").toIntOrNull() ?: 0,
            numSeeders = optString("numSeeders", "0").toIntOrNull() ?: 0,
            isTorrent = btObj != null,
            ratio = optString("ratio", "0")
        )
    }

    // ---------------- 重命名（本地映射，aria2 不支持改名） ----------------

    private fun sp() = appContext!!.getSharedPreferences("motrix_renames", Context.MODE_PRIVATE)

    private fun loadRenames() {
        renames.clear()
        try {
            val s = sp().getString("map", "{}") ?: "{}"
            val o = JSONObject(s)
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = o.optString(k, "")
                if (v.isNotBlank()) renames[k] = v
            }
        } catch (_: Exception) {}
    }

    private fun persistRenames() {
        try {
            val o = JSONObject()
            renames.forEach { (k, v) -> o.put(k, v) }
            sp().edit().putString("map", o.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun applyRename(t: DownloadTask): DownloadTask {
        val np = renames[t.path] ?: return t
        return t.copy(name = File(np).name, path = np)
    }

    /** 记录一次重命名（oldAbs -> newAbs），并立即刷新列表显示与后续打开/导出的路径。 */
    fun recordRename(oldAbs: String, newAbs: String) {
        renames[oldAbs] = newAbs
        persistRenames()
        scope.launch { try { refresh() } catch (_: Exception) {} }
    }

    // ---------------- 操作（异步，结果回调一律切回主线程） ----------------

    fun addUri(text: String, onResult: ((Boolean, String) -> Unit)? = null) = scope.launch {
        val res: Pair<Boolean, String> = try {
            val r = rpc ?: throw IllegalStateException("引擎未运行")
            val urls = extractUrls(text)
            if (urls.isEmpty()) {
                false to "未识别到有效链接（支持 http/https/ftp/magnet/.torrent）"
            } else {
                urls.forEach { u -> r.addUri(JSONArray().put(u), JSONObject()) }
                refresh()
                true to ("已添加 " + urls.size + " 个任务")
            }
        } catch (e: Exception) {
            false to (e.message ?: "添加失败")
        }
        withContext(Dispatchers.Main) { onResult?.invoke(res.first, res.second) }
    }

    /**
     * 从"分享文本"里提取链接，解决"粘贴一大段带标题/提取码的文字就下不动"的问题：
     * - 磁力链 magnet:? 起始取到空白前；
     * - http(s)/ftp/sftp 链接用正则全量提取（支持一段文本里多条）；
     * - 无协议但看起来像域名(含点且无空格)则自动补 http://。
     */
    private fun extractUrls(text: String): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        val magnetIdx = t.indexOf("magnet:?", ignoreCase = true)
        if (magnetIdx >= 0) {
            var s = t.substring(magnetIdx)
            val ws = s.indexOfFirst { it.isWhitespace() }
            if (ws >= 0) s = s.substring(0, ws)
            out.add(s)
        }
        val re = Regex("(?:https?|ftp|sftp)://[^\\s\"'<>\\u3000]+", RegexOption.IGNORE_CASE)
        re.findAll(t).forEach { out.add(it.value) }
        if (out.isEmpty()) {
            val firstLine = t.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            val re2 = Regex("^(?:www\\.)?[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(?:/\\S*)?$")
            if (re2.matches(firstLine)) out.add("http://$firstLine")
        }
        return out.toList()
    }

    fun addTorrentBytes(bytes: ByteArray, selectedFiles1Based: List<Int>? = null, onResult: ((Boolean, String) -> Unit)? = null) = scope.launch {
        val res: Pair<Boolean, String> = try {
            val r = rpc ?: throw IllegalStateException("引擎未运行")
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val opts = JSONObject()
            if (selectedFiles1Based != null && selectedFiles1Based.isNotEmpty()) {
                opts.put("select-file", selectedFiles1Based.joinToString(","))
            }
            val gid = r.addTorrent(b64, opts)
            refresh(); true to gid
        } catch (e: Exception) {
            false to (e.message ?: "添加种子失败")
        }
        withContext(Dispatchers.Main) { onResult?.invoke(res.first, res.second) }
    }

    fun pause(gid: String) = scope.launch { try { rpc?.pause(gid); refresh() } catch (_: Exception) {} }
    fun resume(gid: String) = scope.launch { try { rpc?.unpause(gid); refresh() } catch (_: Exception) {} }
    fun remove(gid: String) = scope.launch {
        // 已完成/错误任务 remove() 会抛错，必须单独 try，确保仍执行 removeResult 从列表清除
        try { rpc?.remove(gid) } catch (_: Exception) {}
        try { rpc?.removeResult(gid) } catch (_: Exception) {}
        refresh()
    }
    fun pauseAll() = scope.launch { try { rpc?.pauseAll(); refresh() } catch (_: Exception) {} }
    fun resumeAll() = scope.launch { try { rpc?.unpauseAll(); refresh() } catch (_: Exception) {} }
    fun purge() = scope.launch { try { rpc?.purgeResults(); refresh() } catch (_: Exception) {} }

    // ---------------- Tracker 管理 ----------------

    fun queryTrackers(cb: (String) -> Unit) = scope.launch {
        val s = try {
            val r = rpc ?: throw IllegalStateException("引擎未运行")
            r.getGlobalOption().optString("bt-tracker", "")
        } catch (e: Exception) { "" }
        withContext(Dispatchers.Main) { cb(s) }
    }

    fun applyTrackers(csv: String, onResult: ((Boolean, String) -> Unit)? = null) = scope.launch {
        val res: Pair<Boolean, String> = try {
            val r = rpc ?: throw IllegalStateException("引擎未运行")
            val o = JSONObject(); o.put("bt-tracker", csv)
            r.changeGlobalOption(o)
            try { appContext?.getSharedPreferences("motrix_prefs_extra", Context.MODE_PRIVATE)?.edit()?.putString("trackers", csv)?.apply() } catch (_: Exception) {}
            true to "ok"
        } catch (e: Exception) { false to (e.message ?: "Tracker 更新失败") }
        withContext(Dispatchers.Main) { onResult?.invoke(res.first, res.second) }
    }

    fun applyGlobalLimit(downloadKb: Int, uploadKb: Int, maxConcurrent: Int) = scope.launch {
        try {
            val o = JSONObject()
            o.put("max-overall-download-limit", if (downloadKb > 0) "${downloadKb}K" else "0")
            o.put("max-overall-upload-limit", if (uploadKb > 0) "${uploadKb}K" else "0")
            o.put("max-concurrent-downloads", maxConcurrent.toString())
            rpc?.changeGlobalOption(o)
        } catch (_: Exception) {}
    }
}
