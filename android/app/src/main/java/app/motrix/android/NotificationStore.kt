package app.motrix.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 下载通知历史（完成/失败），持久化到普通 SharedPreferences。
 * 供「通知中心」列表展示。
 */
object NotificationStore {

    data class Entry(
        val gid: String,
        val name: String,
        val status: String,
        val error: String?,
        val time: Long
    )

    private fun sp(c: Context) =
        c.getSharedPreferences("motrix_notifs", Context.MODE_PRIVATE)

    fun list(c: Context): List<Entry> {
        val raw = sp(c).getString("list", "[]") ?: "[]"
        val out = ArrayList<Entry>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Entry(
                        gid = o.optString("gid"),
                        name = o.optString("name"),
                        status = o.optString("status"),
                        error = if (o.isNull("error")) null else o.optString("error"),
                        time = o.optLong("time")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(c: Context, l: List<Entry>) {
        val arr = JSONArray()
        for (e in l) {
            val o = JSONObject()
            o.put("gid", e.gid)
            o.put("name", e.name)
            o.put("status", e.status)
            o.put("error", e.error ?: JSONObject.NULL)
            o.put("time", e.time)
            arr.put(o)
        }
        sp(c).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, e: Entry) {
        val l = list(c).toMutableList()
        l.add(0, e)
        val trimmed = if (l.size > 100) l.subList(0, 100) else l
        save(c, trimmed)
    }

    fun gids(c: Context): Set<String> = list(c).map { it.gid }.toSet()

    fun clear(c: Context) {
        sp(c).edit().putString("list", "[]").apply()
    }
}
