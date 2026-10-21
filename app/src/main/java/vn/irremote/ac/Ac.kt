package vn.irremote.ac

import kotlinx.serialization.Serializable

enum class AcMode(val label: String) { COOL("Làm lạnh"), HEAT("Sưởi"), DRY("Hút ẩm"), FAN("Quạt"), AUTO("Tự động") }
enum class AcFan(val label: String) { AUTO("Tự động"), LOW("Thấp"), MID("Vừa"), HIGH("Cao") }

@Serializable
data class AcState(val power: Boolean = false, val mode: AcMode = AcMode.COOL, val temp: Int = 25, val fan: AcFan = AcFan.AUTO) {
    fun withTemp(t: Int) = copy(temp = t.coerceIn(16, 32))
}

/**
 * Điều hoà gửi toàn bộ trạng thái trong mỗi lệnh, nên app lưu trạng thái và chọn mã khớp nhất
 * trong các nút đã học/nhập (ví dụ "Cool_25_slow", "Heat_hi", "Off").
 */
object AcResolver {
    private fun tokens(name: String) = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    fun parseMode(t: List<String>): AcMode? = t.firstNotNullOfOrNull {
        when (it) { "cool", "cold", "cooling" -> AcMode.COOL; "heat", "hot", "heating" -> AcMode.HEAT
            "dry", "dh", "dehum" -> AcMode.DRY; "fan", "fanonly", "wind" -> AcMode.FAN; "auto" -> AcMode.AUTO; else -> null }
    }
    fun parseFan(t: List<String>): AcFan? {
        // "auto" xuất hiện lần 2 (sau chế độ) mới là quạt tự động
        val autos = t.count { it == "auto" }
        for (x in t) when (x) {
            "low", "lo", "slow", "min", "quiet" -> return AcFan.LOW
            "mid", "med", "medium" -> return AcFan.MID
            "high", "hi", "fast", "max", "turbo" -> return AcFan.HIGH
        }
        return if (autos >= 2 || (autos == 1 && parseModeNonAuto(t) != null)) AcFan.AUTO else null
    }
    private fun parseModeNonAuto(t: List<String>) = parseMode(t.filter { it != "auto" })
    fun parseTemp(t: List<String>): Int? = t.mapNotNull { it.toIntOrNull() }.firstOrNull { it in 16..32 }
    fun isOff(name: String) = tokens(name).let { "off" in it || "poweroff" in it }

    /** Trả về tên nút phù hợp nhất cho [state], hoặc null nếu không có. */
    fun resolve(state: AcState, names: List<String>): String? {
        if (names.isEmpty()) return null
        if (!state.power) return names.firstOrNull { isOff(it) } ?: names.firstOrNull { tokens(it).contains("power") }
        var best: String? = null; var bestScore = Double.NEGATIVE_INFINITY
        for (n in names) {
            if (isOff(n)) continue
            val t = tokens(n)
            val m = parseMode(t.take(1)) ?: parseMode(t); val tp = parseTemp(t); val f = parseFan(t)
            if (m == null && tp == null && f == null) continue
            var s = 0.0
            s += when { m == state.mode -> 4.0; m == null -> 0.0; else -> -4.0 }
            s += if (tp == null) -0.5 else -kotlin.math.abs(tp - state.temp) * 0.8
            s += when { f == state.fan -> 1.5; f == null -> 0.0; else -> -1.0 }
            if (s > bestScore) { bestScore = s; best = n }
        }
        return best ?: names.firstOrNull { tokens(it).contains("power") || tokens(it).contains("on") }
    }
}
