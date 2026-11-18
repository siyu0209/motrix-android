package app.motrix.android

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.motrix.android.engine.EngineSettings
import java.io.File

/**
 * 设置持久化。优先 EncryptedSharedPreferences，失败则降级普通 SP。
 */
class Prefs(private val context: Context) {

    private val sp: SharedPreferences by lazy {
        try {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "motrix_secure",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("motrix_prefs", Context.MODE_PRIVATE)
        }
    }

    fun defaultDir(): String {
        val ext = context.getExternalFilesDir(null) ?: context.filesDir
        return File(ext, "Downloads").apply { mkdirs() }.absolutePath
    }

    fun loadSettings(): EngineSettings = EngineSettings(
        downloadDir = sp.getString("downloadDir", null) ?: defaultDir(),
        maxConcurrent = sp.getInt("maxConcurrent", 5),
        maxOverallDownloadLimitKb = sp.getInt("dlLimitKb", 0),
        maxOverallUploadLimitKb = sp.getInt("ulLimitKb", 0),
        seedTime = sp.getInt("seedTime", 0),
        rpcPort = sp.getInt("rpcPort", 16800),
        rpcSecret = sp.getString("rpcSecret", "motrix") ?: "motrix",
        maxConnectionPerServer = sp.getInt("maxConnPerServer", 16),
        split = sp.getInt("split", 16),
        btMaxPeers = sp.getInt("btMaxPeers", 128),
        dhtEnabled = sp.getBoolean("dhtEnabled", true),
        listenPortStart = sp.getInt("listenPortStart", 6881),
        listenPortEnd = sp.getInt("listenPortEnd", 6999)
    )

    fun save(s: EngineSettings) {
        sp.edit()
            .putString("downloadDir", s.downloadDir)
            .putInt("maxConcurrent", s.maxConcurrent)
            .putInt("dlLimitKb", s.maxOverallDownloadLimitKb)
            .putInt("ulLimitKb", s.maxOverallUploadLimitKb)
            .putInt("seedTime", s.seedTime)
            .putInt("rpcPort", s.rpcPort)
            .putString("rpcSecret", s.rpcSecret)
            .putInt("maxConnPerServer", s.maxConnectionPerServer)
            .putInt("split", s.split)
            .putInt("btMaxPeers", s.btMaxPeers)
            .putBoolean("dhtEnabled", s.dhtEnabled)
            .putInt("listenPortStart", s.listenPortStart)
            .putInt("listenPortEnd", s.listenPortEnd)
            .apply()
    }

    fun downloadDir(): String = sp.getString("downloadDir", null) ?: defaultDir()

    fun speedMode(): String = sp.getString("speedMode", "high") ?: "high"

    fun setSpeedMode(mode: String) {
        sp.edit().putString("speedMode", mode).apply()
    }

    fun setDownloadDir(dir: String) {
        sp.edit().putString("downloadDir", dir).apply()
    }

    /** 主题模式：system / light / dark */
    fun themeMode(): String = sp.getString("themeMode", "system") ?: "system"
    fun setThemeMode(mode: String) { sp.edit().putString("themeMode", mode).apply() }

    /** 语言模式：system / zh / en */
    fun langMode(): String = sp.getString("langMode", "system") ?: "system"
    fun setLangMode(mode: String) { sp.edit().putString("langMode", mode).apply() }
}
