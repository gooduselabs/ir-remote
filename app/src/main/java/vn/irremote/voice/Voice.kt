package vn.irremote.voice

import java.text.Normalizer

enum class VAction { POWER_ON, POWER_OFF, POWER, VOL_UP, VOL_DOWN, MUTE, CH_UP, CH_DOWN, CHANNEL, AC_TEMP, AC_MODE, SCENE, UNKNOWN }

data class VoiceIntent(val action: VAction, val deviceHint: String? = null, val number: Int? = null, val arg: String? = null)

/** Phân tích câu lệnh tiếng Việt (đã nhận dạng bởi SpeechRecognizer) thành ý định. */
object VoiceParser {
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace('đ', 'd').replace(Regex("\\s+"), " ").trim()

    private val devices = linkedMapOf(
        "AC" to listOf("dieu hoa", "may lanh", "dieu hoa nhiet do"),
        "STB" to listOf("dau thu", "set top", "box"),
        "PROJECTOR" to listOf("may chieu"),
        "FAN" to listOf("quat"),
        "TV" to listOf("tivi", "ti vi", "tv", "truyen hinh"),
    )

    fun parse(text: String): VoiceIntent {
        val t = norm(text)
        val dev = devices.entries.firstOrNull { e -> e.value.any { t.contains(it) } }?.key
        val num = Regex("\\d+").find(t)?.value?.toIntOrNull()
        Regex("(chay|kich hoat|bat) canh (.+)").find(t)?.let { return VoiceIntent(VAction.SCENE, arg = it.groupValues[2].trim()) }
        if (t.contains("tat tieng") || t.contains("im lang") || t.contains("mute")) return VoiceIntent(VAction.MUTE, dev)
        if (t.contains("tang am") || t.contains("to len") || t.contains("to hon")) return VoiceIntent(VAction.VOL_UP, dev)
        if (t.contains("giam am") || t.contains("nho lai") || t.contains("nho di") || t.contains("nho hon")) return VoiceIntent(VAction.VOL_DOWN, dev)
        if (num != null && t.contains("do") && (dev == "AC" || dev == null) && num in 16..32) return VoiceIntent(VAction.AC_TEMP, "AC", num)
        when {
            t.contains("che do lanh") || t.contains("lam lanh") -> return VoiceIntent(VAction.AC_MODE, "AC", arg = "COOL")
            t.contains("suoi") || t.contains("lam nong") -> return VoiceIntent(VAction.AC_MODE, "AC", arg = "HEAT")
            t.contains("hut am") || t.contains("che do kho") -> return VoiceIntent(VAction.AC_MODE, "AC", arg = "DRY")
        }
        if (t.contains("kenh tiep") || t.contains("kenh sau") || t.contains("tang kenh")) return VoiceIntent(VAction.CH_UP, dev)
        if (t.contains("kenh truoc") || t.contains("giam kenh") || t.contains("lui kenh")) return VoiceIntent(VAction.CH_DOWN, dev)
        if (num != null && t.contains("kenh")) return VoiceIntent(VAction.CHANNEL, dev, num)
        if (Regex("\\b(tat|dong)\\b").containsMatchIn(t)) return VoiceIntent(VAction.POWER_OFF, dev)
        if (Regex("\\b(bat|mo)\\b").containsMatchIn(t)) return VoiceIntent(VAction.POWER_ON, dev)
        return VoiceIntent(VAction.UNKNOWN, dev)
    }
}

/** Ánh xạ hành động sang tên nút thường gặp (theo quy ước đặt tên của Flipper-IRDB). */
object ButtonKeys {
    private fun k(s: String) = s.lowercase().replace(Regex("[^a-z0-9+\\-]"), "")
    val synonyms = mapOf(
        VAction.POWER to listOf("power", "pwr", "onoff", "standby", "poweron", "on"),
        VAction.POWER_ON to listOf("poweron", "on", "power", "pwr", "onoff"),
        VAction.POWER_OFF to listOf("poweroff", "off", "power", "pwr", "onoff", "standby"),
        VAction.VOL_UP to listOf("volup", "vol+", "volumeup", "volup1"),
        VAction.VOL_DOWN to listOf("voldn", "voldown", "vol-", "volumedown"),
        VAction.MUTE to listOf("mute"),
        VAction.CH_UP to listOf("chnext", "chup", "ch+", "channelup"),
        VAction.CH_DOWN to listOf("chprev", "chdown", "chdn", "ch-", "channeldown"),
    )
    fun find(action: VAction, names: List<String>): String? {
        val keys = names.associateBy { k(it) }
        return synonyms[action]?.firstNotNullOfOrNull { keys[it] }
    }
    fun digit(d: Char, names: List<String>) = names.firstOrNull { k(it) == d.toString() || k(it) == "num$d" || k(it) == "key$d" }
    fun testCandidate(names: List<String>) = find(VAction.POWER, names) ?: names.firstOrNull()
}
