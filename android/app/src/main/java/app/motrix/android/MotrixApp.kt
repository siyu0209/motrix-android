package app.motrix.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class MotrixApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        applyThemeAndLang()
    }

    /** 应用主题（跟随系统/浅/深）与语言（跟随系统/中文/英文）。 */
    fun applyThemeAndLang() {
        val p = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(
            when (p.themeMode()) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        val tag = when (p.langMode()) {
            "zh" -> "zh-CN"
            "en" -> "en"
            else -> ""
        }
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "motrix.download"
        lateinit var instance: MotrixApp
            private set
    }
}
