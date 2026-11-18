package app.motrix.android.engine

import android.content.Context
import android.os.Build
import java.io.File

/**
 * 管理 aria2c 子进程的生命周期。
 *
 * 关键点：Android 10+ 禁止执行 App 私有可写目录里的二进制（W^X）。
 * 规避方式：把 aria2c 以 `libaria2c.so` 名字打进 APK 的 jniLibs，
 * 让它被解压到只读可执行的 nativeLibraryDir，再直接 execve 该路径。
 */
class Aria2Process(private val context: Context) {

    @Volatile private var process: Process? = null

    @Volatile var binaryPath: String? = null
        private set

    @Volatile var lastLogLine: String = ""
        private set

    /** aria2 进程退出码（用于诊断：139/132 表示段错误/非法指令，多为二进制与内核不兼容）。 */
    @Volatile var lastExitCode: Int? = null
        private set

    private var logThread: Thread? = null

    /** 解析可执行的 aria2c 路径（优先 nativeLibraryDir，其次 assets 兜底）。 */
    fun resolveBinary(): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val lib = File(nativeDir, "libaria2c.so")
        if (lib.exists()) {
            lib.setExecutable(true)
            return lib.absolutePath
        }
        // 兜底：从 assets 复制到 filesDir（主要照顾 <API29 或个别 ROM）
        val target = File(context.filesDir, "bin/aria2c")
        if (!target.exists() || target.length() == 0L) {
            target.parentFile?.mkdirs()
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val assetName = if (abi.contains("64")) "aria2/arm64-v8a/aria2c" else "aria2/armeabi-v7a/aria2c"
            try {
                context.assets.open(assetName).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                throw IllegalStateException("内置 aria2 未找到（$assetName）：${e.message}")
            }
        }
        target.setExecutable(true)
        return target.absolutePath
    }

    /** 运行 `aria2c --version` 做一次性自检，返回 exit + 输出（用于诊断启动失败）。 */
    fun probeVersion(): String {
        return try {
            val bin = try { resolveBinary() } catch (e: Exception) { return "解析二进制失败：${e.message}" }
            val pb = ProcessBuilder(bin, "--version")
            pb.redirectErrorStream(true)
            pb.environment()["HOME"] = context.filesDir.absolutePath
            val p = pb.start()
            val out = StringBuilder()
            p.inputStream.bufferedReader().useLines { lines -> lines.forEach { if (out.length < 2000) out.append(it).append('\n') } }
            val code = try { p.waitFor() } catch (_: Exception) { -1 }
            "exit=$code\n${out.toString().trim().ifBlank { "(无输出)" }}"
        } catch (e: Exception) {
            "exec 失败：${e.javaClass.simpleName}: ${e.message}"
        }
    }

    fun start(settings: EngineSettings): Boolean {
        stop()
        val bin = resolveBinary()
        binaryPath = bin

        // 证书
        val cacert = File(context.filesDir, "cacert.pem")
        if (!cacert.exists() || cacert.length() == 0L) {
            try { context.assets.open("cacert.pem").use { i -> cacert.outputStream().use { o -> i.copyTo(o) } } } catch (_: Exception) {}
        }

        val dlDir = File(settings.downloadDir).apply { mkdirs() }
        val aria2Log = File(context.filesDir, "aria2.log")

        val args = mutableListOf(
            bin,
            "--enable-rpc=true",
            "--rpc-listen-all=false",
            "--rpc-listen-port=${settings.rpcPort}",
            "--rpc-secret=${settings.rpcSecret}",
            "--rpc-allow-origin-all=true",
            "--dir=${dlDir.absolutePath}",
            "--continue=true",
            "--max-concurrent-downloads=${settings.maxConcurrent}",
            "--max-connection-per-server=${settings.maxConnectionPerServer}",
            "--split=${settings.split}",
            "--min-split-size=1M",
            "--file-allocation=none",
            "--enable-color=false",
            "--console-log-level=warn",
            "--summary-interval=0",
            "--auto-file-renaming=true",
            "--allow-overwrite=false",
            "--follow-torrent=true",
            "--bt-enable-lpd=false",
            "--bt-save-metadata=true",
            "--enable-dht=${settings.dhtEnabled}",
            "--listen-port=${settings.listenPortStart}-${settings.listenPortEnd}",
            "--dht-listen-port=${settings.listenPortStart}-${settings.listenPortEnd}",
            "--enable-peer-exchange=true",
            "--bt-max-peers=${settings.btMaxPeers}",
            "--bt-tracker-connect-timeout=10",
            "--bt-tracker-timeout=10",
            "--bt-tracker=" +
                "udp://tracker.opentrackr.org:1337/announce," +
                "udp://open.stealth.si:80/announce," +
                "udp://tracker.torrent.eu.org:451/announce," +
                "udp://tracker.moeking.me:6969/announce," +
                "udp://explodie.org:6969/announce," +
                "udp://tracker.openbittorrent.com:6969/announce," +
                "udp://exodus.desync.com:6969/announce," +
                "udp://tracker.theoks.net:6969/announce," +
                "udp://open.demonii.com:1337/announce," +
                "udp://tracker1.bt.moack.co.kr:80/announce," +
                "udp://tracker.tiny-vps.com:6969/announce," +
                "udp://opentracker.i2p.rocks:6969/announce," +
                "udp://tracker.dler.org:6969/announce," +
                "udp://tracker.birkenwald.de:6969/announce," +
                "udp://tracker.0x7c0.com:6969/announce," +
                "udp://tracker.altrosky.nl:6969/announce," +
                "udp://tracker.qu.ax:6969/announce," +
                "udp://tracker.fnix.net:6969/announce," +
                "udp://tracker.ololosh.space:6969/announce," +
                "udp://tracker.leech.ie:1337/announce," +
                "https://tracker.tamersunion.org:443/announce," +
                "https://tracker.gcrenwp.top:443/announce," +
                "https://tr.burnabyhighstar.com:443/announce," +
                "https://tracker.zhuqiy.com:443/announce," +
                "https://tracker.bt4g.com:443/announce," +
                "https://tracker.nexusfs.xyz:443/announce," +
                "https://open.tracker.cl:443/announce," +
                "https://tracker.renfei.net:443/announce," +
                "http://tracker.openbittorrent.com:80/announce," +
                "http://bt.okmp3.ru:2710/announce",
            "--seed-time=${settings.seedTime}",
            "--log=${aria2Log.absolutePath}",
            "--log-level=warn"
        )
        if (cacert.exists()) args.add("--ca-certificate=${cacert.absolutePath}")
        if (settings.maxOverallDownloadLimitKb > 0) args.add("--max-overall-download-limit=${settings.maxOverallDownloadLimitKb}K")
        if (settings.maxOverallUploadLimitKb > 0) args.add("--max-overall-upload-limit=${settings.maxOverallUploadLimitKb}K")

        val pb = ProcessBuilder(args)
        pb.redirectErrorStream(true)
        pb.environment()["HOME"] = context.filesDir.absolutePath
        pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
        pb.directory(context.filesDir)

        val p = pb.start()
        process = p
        lastExitCode = null

        // 退出码侦测：进程若立刻崩溃，这里能拿到 139(SIGSEGV)/132(SIGILL) 等
        Thread {
            try { lastExitCode = p.waitFor() } catch (_: Exception) {}
        }.also { it.isDaemon = true; it.start() }

        logThread = Thread {
            try {
                p.inputStream.bufferedReader().useLines { lines ->
                    for (ln in lines) lastLogLine = ln
                }
            } catch (_: Exception) {
            }
        }.also { it.isDaemon = true; it.start() }
        return true
    }

    fun isRunning(): Boolean = process?.isAlive == true

    fun stop() {
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        logThread = null
    }
}
