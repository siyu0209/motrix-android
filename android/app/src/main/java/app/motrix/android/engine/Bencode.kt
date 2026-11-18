package app.motrix.android.engine

/**
 * 极简 bencode 解码 + .torrent 文件清单解析。
 * 只用于在"添加种子"时展示文件列表并支持勾选，不做完整性/私有种子的完整语义。
 */
object Bencode {
    fun decode(bytes: ByteArray): Any? = try {
        var i = 0
        fun parse(): Any {
            val c = bytes[i].toInt().toChar()
            return when {
                c == 'i' -> {
                    i++
                    val sb = StringBuilder()
                    while (bytes[i].toInt().toChar() != 'e') { sb.append(bytes[i].toInt().toChar()); i++ }
                    i++
                    sb.toString().toLongOrNull() ?: 0L
                }
                c == 'l' -> {
                    i++
                    val list = ArrayList<Any>()
                    while (bytes[i].toInt().toChar() != 'e') list.add(parse())
                    i++
                    list
                }
                c == 'd' -> {
                    i++
                    val map = LinkedHashMap<String, Any>()
                    while (bytes[i].toInt().toChar() != 'e') {
                        val k = parse()
                        val key = if (k is ByteArray) String(k, Charsets.UTF_8) else k.toString()
                        map[key] = parse()
                    }
                    i++
                    map
                }
                c.isDigit() -> {
                    val sb = StringBuilder()
                    while (bytes[i].toInt().toChar() != ':') { sb.append(bytes[i].toInt().toChar()); i++ }
                    i++
                    val len = sb.toString().toIntOrNull() ?: 0
                    val arr = bytes.copyOfRange(i, (i + len).coerceAtMost(bytes.size))
                    i += len
                    arr
                }
                else -> { i++; "" } // 容错：跳过异常字节
            }
        }
        parse()
    } catch (e: Exception) {
        null
    }
}

data class TorrentFile(val index: Int, val path: String, val size: Long)

object TorrentParser {
    /** 返回种子内的文件清单（multi-file 用 files 列表；单文件用 length/name，返回 1 条）。 */
    fun files(bytes: ByteArray): List<TorrentFile> {
        val root = Bencode.decode(bytes) as? Map<*, *> ?: return emptyList()
        val info = root["info"] as? Map<*, *> ?: return emptyList()
        val out = ArrayList<TorrentFile>()
        val files = info["files"] as? List<*>
        if (files != null) {
            var idx = 0
            for (f in files) {
                idx++
                val m = f as? Map<*, *> ?: continue
                val len = (m["length"] as? Long) ?: 0L
                val pl = (m["path"] as? List<*>)?.joinToString("/") { String((it as? ByteArray) ?: ByteArray(0), Charsets.UTF_8) } ?: ""
                out.add(TorrentFile(idx, pl, len))
            }
        } else if (info.containsKey("length")) {
            val len = (info["length"] as? Long) ?: 0L
            val nm = String((info["name"] as? ByteArray) ?: ByteArray(0), Charsets.UTF_8)
            out.add(TorrentFile(1, nm, len))
        }
        return out
    }
}
