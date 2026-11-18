package app.motrix.android.web

import android.content.Context
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 本地兼容 server（NanoWSD）：
 *  1) 静态托管 assets/renderer-web 下的文件
 *  2) 提供 /rpc/auth 系列接口（本地单用户：直接 authed=true）
 *  3) 把 /rpc/{query|command}/<channel> 转给 RpcDispatcher（映射到端内 aria2）
 *  4) /rpc/events WebSocket：心跳 + 周期性 stats/task 推送
 *
 * 这样原版 Motrix Web UI 以同源方式加载后，transport 会自然把后端指向本机，
 * 无需修改渲染器（见 src/renderer/lib/web-base-url.ts）。
 */
class BridgeServer(
    context: Context,
    port: Int,
    private val dispatcher: RpcDispatcher
) : NanoWSD(port) {

    private val app = context.applicationContext
    private val root = "renderer-web"
    private val sockets = CopyOnWriteArraySet<WebSocket>()

    @Volatile
    private var ticker: Thread? = null

    override fun serveHttp(session: IHTTPSession): Response {
        val uri = session.uri.trimStart('/').ifEmpty { "index.html" }

        when {
            uri == "healthz" || uri == "health" -> return text("ok")
            uri == "__mtx/mobile-adapt.css" -> return assetResponse("mobile-adapt.css", "text/css")
            uri == "__mtx/mobile-adapt.js" -> return assetResponse("mobile-adapt.js", "application/javascript")
            uri == "rpc/auth/status" -> return json(
                JSONObject().put("authed", true).put("mode", "unrestricted").put("canLogout", false)
            )
            uri == "rpc/auth/login" -> return json(JSONObject().put("ok", true))
            uri == "rpc/auth/logout" -> return json(JSONObject().put("ok", true))
            uri.startsWith("rpc/query/") || uri.startsWith("rpc/command/") -> return handleRpc(session, uri)
            uri.startsWith("api/") || uri.startsWith("bridge") -> return json(JSONObject().put("ok", true))
        }

        var bytes = load(uri)
        var servedPath = uri
        if (bytes == null) {
            bytes = load("index.html")
            servedPath = "index.html"
        }
        val body = bytes ?: return text(
            "assets/$root 尚未打包（先复制 dist/renderer-web 到 app/src/main/assets/$root）",
            Response.Status.NOT_FOUND
        )
        if (servedPath.endsWith(".html")) {
            val vw = session.parameters["vw"]?.firstOrNull()?.toIntOrNull()
            val html = injectMobile(String(body, Charsets.UTF_8), vw)
            val b2 = html.toByteArray(Charsets.UTF_8)
            return newFixedLengthResponse(
                Response.Status.OK, "text/html", ByteArrayInputStream(b2), b2.size.toLong()
            )
        }
        return newFixedLengthResponse(
            Response.Status.OK, mimeOf(servedPath), ByteArrayInputStream(body), body.size.toLong()
        )
    }

    private fun assetResponse(name: String, mime: String): Response {
        val b = try {
            app.assets.open(name).use { it.readBytes() }
        } catch (e: Exception) {
            return text("not found", Response.Status.NOT_FOUND)
        }
        return newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(b), b.size.toLong())
    }

    /** 把移动端适配的 viewport / css / js 注入到 index.html 的 </head> 之前。 */
    private fun injectMobile(html: String, vw: Int? = null): String {
        if (html.contains("/__mtx/mobile-adapt")) return html
        // 调试视口模拟：?vw=W 时用固定 CSS 宽度，令媒体查询按 W 生效（模拟任意设备宽度）
        val viewport = if (vw != null && vw in 200..4000)
            "width=$vw, user-scalable=no, viewport-fit=cover"
        else
            "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover"
        val snippet = "<meta name=\"viewport\" content=\"$viewport\">" +
            "<link rel=\"stylesheet\" href=\"/__mtx/mobile-adapt.css\">" +
            "<script defer src=\"/__mtx/mobile-adapt.js\"></script>"
        val idx = html.indexOf("</head>", ignoreCase = true)
        return if (idx >= 0) html.substring(0, idx) + snippet + html.substring(idx) else snippet + html
    }

    private fun handleRpc(session: IHTTPSession, uri: String): Response {
        val parts = uri.split("/")
        val method = parts.getOrElse(1) { "query" }
        val rawChannel = parts.drop(2).joinToString("/")
        val channel = try {
            java.net.URLDecoder.decode(rawChannel, "UTF-8")
        } catch (_: Exception) {
            rawChannel
        }
        val args = parseArgs(readBody(session))
        return try {
            val value = dispatcher.handle(method, channel, args)
            if (value == null) {
                newFixedLengthResponse(Response.Status.OK, "application/json", "null")
            } else {
                jsonRaw(value)
            }
        } catch (e: Exception) {
            jsonRaw(
                JSONObject().put("ok", false)
                    .put("error", JSONObject().put("code", "bridge"))
                    .put("message", e.message ?: "")
            )
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        val uri = handshake.uri
        if (uri != "/rpc/events" && uri != "/rpc/events/") return null
        return BridgeSocket(handshake)
    }

    private inner class BridgeSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            sockets.add(this)
            ensureTicker()
            try {
                send("""{"type":"heartbeat","version":1}""")
            } catch (_: Exception) {
            }
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            sockets.remove(this)
        }

        override fun onMessage(message: WebSocketFrame) {
            // 客户端无需发送数据帧；忽略。
        }

        override fun onPong(pong: WebSocketFrame) {}

        override fun onException(e: java.io.IOException) {
            sockets.remove(this)
            try {
                close(WebSocketFrame.CloseCode.InternalServerError, "err", false)
            } catch (_: Exception) {
            }
        }
    }

    private fun ensureTicker() {
        if (ticker != null) return
        val t = Thread {
            var tick = 0
            while (true) {
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    break
                }
                tick++
                val heartbeat = tick % 7 == 0
                for (s in sockets) {
                    try {
                        val stats = runCatching { dispatcher.statsPayload() }.getOrNull()
                        if (stats != null) s.send(frame("event:statsUpdated", stats))
                        val tasks = runCatching { dispatcher.tasksPayload() }.getOrNull()
                        if (tasks is JSONArray) {
                            for (i in 0 until tasks.length()) {
                                s.send(frame("event:taskUpdated", tasks.get(i)))
                            }
                        }
                        if (heartbeat) s.send("""{"type":"heartbeat","version":1}""")
                    } catch (_: Exception) {
                        sockets.remove(s)
                    }
                }
            }
        }
        t.isDaemon = true
        ticker = t
        t.start()
    }

    private fun frame(channel: String, payload: Any?): String {
        val args = JSONArray()
        when (payload) {
            null -> args.put(JSONObject.NULL)
            is JSONObject -> args.put(payload)
            is JSONArray -> args.put(payload)
            is String -> args.put(payload)
            is Number -> args.put(payload)
            is Boolean -> args.put(payload)
            else -> args.put(payload.toString())
        }
        return JSONObject().put("channel", channel).put("args", args).toString()
    }

    private fun readBody(session: IHTTPSession): String {
        return try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            map["postData"] ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseArgs(body: String): List<Any?> {
        if (body.isBlank()) return emptyList()
        return try {
            val obj = JSONObject(body)
            val arr = obj.optJSONArray("args") ?: return emptyList()
            val out = ArrayList<Any?>(arr.length())
            for (i in 0 until arr.length()) {
                val v = arr.opt(i)
                out.add(if (v == JSONObject.NULL) null else v)
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun text(msg: String, status: Response.Status = Response.Status.OK): Response =
        newFixedLengthResponse(status, "text/plain", msg)

    private fun json(obj: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", obj.toString())

    private fun jsonRaw(value: Any): Response {
        val s = when (value) {
            is JSONObject, is JSONArray -> value.toString()
            is String -> JSONObject.quote(value)
            else -> JSONObject.wrap(value)?.toString() ?: "null"
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", s)
    }

    private fun load(path: String): ByteArray? = try {
        app.assets.open("$root/$path").use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun mimeOf(path: String): String = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".js") || path.endsWith(".mjs") -> "application/javascript"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".webp") -> "image/webp"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".woff") -> "font/woff"
        path.endsWith(".ttf") -> "font/ttf"
        path.endsWith(".wasm") -> "application/wasm"
        path.endsWith(".ico") -> "image/x-icon"
        path.endsWith(".map") -> "application/json"
        else -> "application/octet-stream"
    }
}
