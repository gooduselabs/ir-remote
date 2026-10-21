package vn.irremote.ir

/** Tín hiệu IR sẵn sàng phát: tần số sóng mang (Hz) + chuỗi mark/space (µs), bắt đầu bằng mark. */
class IrSignal(val frequency: Int, val pattern: IntArray) {
    val totalMicros: Long get() = pattern.fold(0L) { a, b -> a + b }
    override fun toString() = "IrSignal($frequency Hz, ${pattern.size} xung)"
}

/** Ghép mark/space, tự gộp các đoạn cùng mức và bỏ space ở đầu. */
class PatternBuilder {
    private val list = ArrayList<Int>()
    private var lastMark: Boolean? = null
    fun mark(us: Int) = add(true, us)
    fun space(us: Int) = add(false, us)
    fun add(isMark: Boolean, us: Int): PatternBuilder {
        if (us <= 0) return this
        if (list.isEmpty() && !isMark) return this
        if (lastMark == isMark) list[list.size - 1] += us else { list.add(us); lastMark = isMark }
        return this
    }
    /** Đảm bảo khung kéo dài tối thiểu [periodUs] tính từ [startIndex]. */
    fun padTo(startIndex: Int, periodUs: Int) {
        val used = list.drop(startIndex).sum()
        if (used < periodUs) space(periodUs - used)
    }
    val size get() = list.size
    fun build(freq: Int) = IrSignal(freq, list.toIntArray())
}
