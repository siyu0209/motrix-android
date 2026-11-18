package app.motrix.android.web

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream

/**
 * 轻量本地静态服务器：把打包进 assets/renderer-web 的原版 Motrix Web UI
 * 通过 http://127.0.0.1:<port>/ 提供出来，让 WebView 以同源方式加载，
 * 从而渲染器（web target）会用 location.origin 作为后端基址（见
 * src/renderer/lib/web-base-url.ts）。后续在此之上扩展 REST/WS 兼容桥。
 */
class AssetServer(context: Context, port: Int) : NanoHTTPD(port) {

    private val app = context.applicationContext
    private val root = "renderer-web"

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimStart('/').ifEmpty { "index.html" }

        if (uri == "health") {
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
        }

        // 预留：API 命名空间，后续接 REST 兼容桥
        if (uri.startsWith("api/") || uri.startsWith("rpc/") || uri.startsWith("bridge")) {
            return newFixedLengthResponse(
                Response.Status.NOT_IMPLEMENTED, "application/json",
                """{"error":"bridge-not-ready"}"""
            )
        }

        var bytes = load(uri)
        var servedPath = uri
        if (bytes == null) {
            bytes = load("index.html")
            servedPath = "index.html"
        }
        val body = bytes
        if (body == null) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, "text/plain",
                "assets/$root 尚未打包（请先把 dist/renderer-web 复制到 app/src/main/assets/$root）"
            )
        }
        val mime = mimeOf(servedPath)
        return newFixedLengthResponse(
            Response.Status.OK, mime, ByteArrayInputStream(body), body.size.toLong()
        )
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
