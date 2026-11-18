package app.motrix.android.engine

/** 一条下载任务（由 aria2 的 status 对象映射而来）。 */
data class DownloadTask(
    val gid: String,
    val name: String,
    val status: String,          // active / waiting / paused / error / complete / removed
    val totalBytes: Long,
    val doneBytes: Long,
    val downloadSpeed: Long,     // B/s
    val uploadSpeed: Long,       // B/s
    val dir: String,
    val path: String,
    val errorMessage: String,
    val connections: Int,
    val numSeeders: Int,
    val isTorrent: Boolean,
    val ratio: String
) {
    /** 0..100 */
    val progress: Int
        get() = if (totalBytes > 0L) ((doneBytes * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0

    val isActive: Boolean get() = status == "active"
    val isWaiting: Boolean get() = status == "waiting"
    val isPaused: Boolean get() = status == "paused"
    val isComplete: Boolean get() = status == "complete"
    val isError: Boolean get() = status == "error" || status == "removed"
}

/** aria2.getGlobalStat 的映射。 */
data class GlobalStat(
    val downloadSpeed: Long = 0L,   // B/s
    val uploadSpeed: Long = 0L,     // B/s
    val numActive: Int = 0,
    val numWaiting: Int = 0,
    val numStopped: Int = 0
)

/** 引擎设置（持久化在 Prefs 中）。 */
data class EngineSettings(
    val downloadDir: String,
    val maxConcurrent: Int = 5,
    val maxOverallDownloadLimitKb: Int = 0, // KB/s，0=不限
    val maxOverallUploadLimitKb: Int = 0,
    val seedTime: Int = 0,                  // 分钟，0=不做种
    val rpcPort: Int = 16800,
    val rpcSecret: String = "motrix",
    val maxConnectionPerServer: Int = 16,
    val split: Int = 16,
    val btMaxPeers: Int = 128,
    val dhtEnabled: Boolean = true,
    val listenPortStart: Int = 6881,
    val listenPortEnd: Int = 6999
)
