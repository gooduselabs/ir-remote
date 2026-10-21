package vn.irremote.ir

/** Một nút đọc từ file .ir (định dạng văn bản "IR signals file" của Flipper Zero). */
data class IrCode(
    val name: String, val protocol: String, val address: Long = 0, val command: Long = 0,
    val frequency: Int = 38_000, val data: String = "",
) {
    fun toSignal() = Encoders.build(protocol, address, command, frequency, data)
}

/** Trình đọc/ghi .ir tự viết theo mô tả định dạng công khai (khối key: value, ngăn bởi '#'). */
object FlipperIr {
    fun parse(text: String): List<IrCode> {
        val out = ArrayList<IrCode>()
        var cur = HashMap<String, String>()
        fun flush() {
            val name = cur["name"]
            if (name != null) {
                val type = cur["type"]?.lowercase()
                runCatching {
                    out += if (type == "raw") IrCode(name, "RAW", frequency = cur["frequency"]?.toIntOrNull() ?: 38_000, data = cur["data"].orEmpty())
                    else IrCode(name, cur["protocol"] ?: "NEC", leHex(cur["address"]), leHex(cur["command"]))
                }
            }
            cur = HashMap()
        }
        for (line0 in text.lineSequence()) {
            val line = line0.trim()
            if (line.startsWith("#")) { flush(); continue }
            val i = line.indexOf(':'); if (i <= 0) continue
            val k = line.substring(0, i).trim().lowercase(); val v = line.substring(i + 1).trim()
            if (k == "name" && cur.containsKey("name")) flush()
            if (k == "data" && cur.containsKey("data")) cur[k] = cur[k] + " " + v else cur[k] = v
        }
        flush()
        return out
    }

    /** "07 00 00 00" → 0x00000007 (little-endian). */
    fun leHex(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val parts = s.trim().split(Regex("\\s+"))
        var v = 0L
        parts.forEachIndexed { i, p -> v = v or (p.toLong(16) shl (8 * i)) }
        return v
    }

    fun toLeHex(v: Long) = (0 until 4).joinToString(" ") { "%02X".format((v shr (8 * it)) and 0xFF) }

    fun write(codes: List<IrCode>): String = buildString {
        append("Filetype: IR signals file\nVersion: 1\n")
        for (c in codes) {
            append("# \nname: ${c.name}\n")
            if (c.protocol.equals("RAW", true)) append("type: raw\nfrequency: ${c.frequency}\nduty_cycle: 0.330000\ndata: ${c.data}\n")
            else append("type: parsed\nprotocol: ${c.protocol}\naddress: ${toLeHex(c.address)}\ncommand: ${toLeHex(c.command)}\n")
        }
    }
}
