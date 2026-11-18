package app.motrix.android.engine

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 aria2 JSON-RPC 客户端（HTTP POST，token 认证）。
 * 只依赖 org.json + HttpURLConnection，零第三方库。
 */
class Aria2Rpc(private val port: Int, val secret: String) {

    private val endpoint: String get() = "http://127.0.0.1:$port/jsonrpc"

    @Volatile
    var lastError: String? = null
        private set

    @Throws(Exception::class)
    fun call(method: String, vararg params: Any?): Any? {
        val arr = JSONArray()
        arr.put("token:$secret")
        for (p in params) arr.put(p)
        val req = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", "motrix-android")
            put("method", method)
            put("params", arr)
        }
        val body = req.toString().toByteArray(Charsets.UTF_8)
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 10000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (text.isBlank()) throw RuntimeException("aria2 无响应 (HTTP $code)")
            val obj = JSONObject(text)
            if (obj.has("error")) {
                val e = obj.getJSONObject("error")
                val msg = "${e.optString("message")} (code ${e.optInt("code")})"
                lastError = msg
                throw RuntimeException(msg)
            }
            return obj.opt("result")
        } finally {
            conn.disconnect()
        }
    }

    fun versionOk(): Boolean = try { call("aria2.getVersion"); true } catch (_: Exception) { false }

    fun tellActive(): JSONArray = (call("aria2.tellActive") as? JSONArray) ?: JSONArray()
    fun tellWaiting(): JSONArray = (call("aria2.tellWaiting", 0, 1000) as? JSONArray) ?: JSONArray()
    fun tellStopped(): JSONArray = (call("aria2.tellStopped", 0, 1000) as? JSONArray) ?: JSONArray()
    fun globalStat(): JSONObject = (call("aria2.getGlobalStat") as? JSONObject) ?: JSONObject()
    fun getGlobalOption(): JSONObject = (call("aria2.getGlobalOption") as? JSONObject) ?: JSONObject()

    fun addUri(urls: JSONArray, options: JSONObject): String =
        call("aria2.addUri", urls, options).toString()

    fun addTorrent(base64: String, options: JSONObject): String =
        call("aria2.addTorrent", base64, JSONArray(), options).toString()

    fun pause(gid: String) { call("aria2.forcePause", gid) }
    fun unpause(gid: String) { call("aria2.unpause", gid) }
    fun remove(gid: String) { call("aria2.forceRemove", gid) }
    fun removeResult(gid: String) { try { call("aria2.removeDownloadResult", gid) } catch (_: Exception) {} }
    fun pauseAll() { call("aria2.pauseAll") }
    fun unpauseAll() { call("aria2.unpauseAll") }
    fun purgeResults() { call("aria2.purgeDownloadResult") }
    fun changeGlobalOption(opts: JSONObject) { call("aria2.changeGlobalOption", opts) }
    fun shutdown() { try { call("aria2.shutdown") } catch (_: Exception) {} }
}
