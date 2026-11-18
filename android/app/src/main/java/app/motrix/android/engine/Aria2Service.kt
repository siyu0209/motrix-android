package app.motrix.android.engine

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.motrix.android.MainActivity
import app.motrix.android.MotrixApp
import app.motrix.android.Prefs
import app.motrix.android.R
import kotlinx.coroutines.launch

/**
 * 前台服务：进程常驻，保证 aria2 引擎在后台继续下载。
 */
class Aria2Service : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundCompat()
        Engine.init(applicationContext)
        val settings = Prefs(applicationContext).loadSettings()
        lifecycleScope.launch { Engine.start(settings) }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, MotrixApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(R.drawable.ic_stat_download)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    override fun onDestroy() {
        Engine.stop()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 1001
    }
}
