package vn.irremote.ir

enum class Slot { POWER, MUTE, VOL_UP, VOL_DN, CH_UP, CH_DN, UP, DOWN, LEFT, RIGHT, OK, MENU, BACK, HOME, SOURCE, INFO,
    D0, D1, D2, D3, D4, D5, D6, D7, D8, D9 }

/** Xếp các nút đã nhập vào vị trí trên remote thật (D-pad, rocker, bàn phím số...). Phần còn lại vào "extras". */
object RemoteLayout {
    private fun k(s: String) = s.lowercase().replace(Regex("[^a-z0-9+\\-]"), "")
    private val syn: Map<Slot, List<String>> = mapOf(
        Slot.POWER to listOf("power", "pwr", "onoff", "standby", "poweron", "power1", "on"),
        Slot.MUTE to listOf("mute"),
        Slot.VOL_UP to listOf("volup", "vol+", "volumeup"), Slot.VOL_DN to listOf("voldn", "voldown", "vol-", "volumedown"),
        Slot.CH_UP to listOf("chnext", "chup", "ch+", "channelup", "pageup"), Slot.CH_DN to listOf("chprev", "chdown", "chdn", "ch-", "channeldown", "pagedown"),
        Slot.UP to listOf("up", "navup", "arrowup", "cursorup"), Slot.DOWN to listOf("down", "navdown", "arrowdown", "cursordown"),
        Slot.LEFT to listOf("left", "navleft", "arrowleft", "cursorleft"), Slot.RIGHT to listOf("right", "navright", "arrowright", "cursorright"),
        Slot.OK to listOf("ok", "enter", "select", "center", "confirm"),
        Slot.MENU to listOf("menu", "setup", "settings"), Slot.BACK to listOf("back", "return", "exit"),
        Slot.HOME to listOf("home", "smarthub"), Slot.SOURCE to listOf("source", "input", "inputsource", "av", "tvav"),
        Slot.INFO to listOf("info", "display", "guide"),
    ) + (0..9).associate { Slot.valueOf("D$it") to listOf("$it", "num$it", "key$it", "digit$it") }

    data class Mapped<T>(val slots: Map<Slot, T>, val extras: List<T>) {
        val hasDpad get() = listOf(Slot.UP, Slot.DOWN, Slot.LEFT, Slot.RIGHT).count { it in slots } >= 2
        val hasDigits get() = (0..9).count { Slot.valueOf("D$it") in slots } >= 6
    }

    fun <T> map(items: List<T>, name: (T) -> String): Mapped<T> {
        val slots = LinkedHashMap<Slot, T>(); val used = HashSet<Int>()
        for ((slot, keys) in syn) for (key in keys) {
            val i = items.indices.firstOrNull { it !in used && k(name(items[it])) == key } ?: continue
            slots[slot] = items[i]; used += i; break
        }
        return Mapped(slots, items.filterIndexed { i, _ -> i !in used })
    }
}
