package app.motrix.android.util

import java.util.Locale

object Fmt {
    fun size(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024.0 && i < units.size - 1) { v /= 1024.0; i++ }
        return if (i == 0) "$bytes ${units[i]}" else String.format(Locale.US, "%.2f %s", v, units[i])
    }

    fun speed(bytesPerSec: Long): String = size(bytesPerSec) + "/s"

    fun eta(done: Long, total: Long, speed: Long): String {
        if (speed <= 0L || total <= 0L) return "--"
        val remain = (total - done).coerceAtLeast(0L)
        val sec = remain / speed
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
