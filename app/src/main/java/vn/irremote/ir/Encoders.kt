package vn.irremote.ir

import kotlin.math.roundToInt

/**
 * Bộ mã hoá giao thức IR, tự viết theo các đặc tả công khai (timing chuẩn của NEC, Philips RC5/RC6,
 * Sony SIRC, Kaseikyo, JVC, Pronto). Không sao chép mã nguồn của bên thứ ba.
 */
object Encoders {
    private fun PatternBuilder.pulseDistanceLsb(value: Long, bits: Int, mark: Int, zero: Int, one: Int) {
        for (i in 0 until bits) { mark(mark); space(if ((value shr i) and 1L == 1L) one else zero) }
    }

    /** NEC chuẩn: địa chỉ 8 bit, lệnh 8 bit, kèm byte đảo. */
    fun nec(address: Int, command: Int): IrSignal {
        val a = address and 0xFF; val c = command and 0xFF
        val v = a.toLong() or ((a.inv() and 0xFF).toLong() shl 8) or (c.toLong() shl 16) or ((c.inv() and 0xFF).toLong() shl 24)
        return necFrame(v)
    }

    /** NEC mở rộng: địa chỉ 16 bit, lệnh 16 bit (gửi nguyên, LSB trước). */
    fun necExt(address: Int, command: Int): IrSignal =
        necFrame((address and 0xFFFF).toLong() or ((command and 0xFFFF).toLong() shl 16))

    private fun necFrame(v: Long): IrSignal {
        val b = PatternBuilder().mark(9000).space(4500)
        b.pulseDistanceLsb(v, 32, 560, 560, 1690)
        b.mark(560); b.padTo(0, 108_000)
        return b.build(38_000)
    }

    /** Samsung32: header 4500/4500, địa chỉ lặp 2 lần, lệnh + lệnh đảo. */
    fun samsung32(address: Int, command: Int): IrSignal {
        val a = address and 0xFF; val c = command and 0xFF
        val v = a.toLong() or (a.toLong() shl 8) or (c.toLong() shl 16) or ((c.inv() and 0xFF).toLong() shl 24)
        val b = PatternBuilder().mark(4500).space(4500)
        b.pulseDistanceLsb(v, 32, 560, 560, 1690)
        b.mark(560); b.padTo(0, 108_000)
        return b.build(38_000)
    }

    /** JVC: header 8400/4200, địa chỉ 8 bit + lệnh 8 bit. */
    fun jvc(address: Int, command: Int): IrSignal {
        val v = (address and 0xFF).toLong() or ((command and 0xFF).toLong() shl 8)
        val b = PatternBuilder().mark(8400).space(4200)
        b.pulseDistanceLsb(v, 16, 526, 526, 1574)
        b.mark(526); b.padTo(0, 60_000)
        return b.build(38_000)
    }

    /** Philips RC5 (và RC5X khi lệnh >= 64): Manchester 889 µs, 14 bit. 1 = space→mark. */
    fun rc5(address: Int, command: Int, toggle: Boolean = false): IrSignal {
        val t = 889
        val s2 = if (command and 0x40 != 0) 0 else 1
        val bits = ArrayList<Int>()
        bits += 1; bits += s2; bits += if (toggle) 1 else 0
        for (i in 4 downTo 0) bits += (address shr i) and 1
        for (i in 5 downTo 0) bits += (command shr i) and 1
        val b = PatternBuilder()
        for (bit in bits) { if (bit == 1) { b.space(t); b.mark(t) } else { b.mark(t); b.space(t) } }
        b.padTo(0, 113_778)
        return b.build(36_000)
    }

    /** Philips RC6 mode 0: leader 2666/889, Manchester 444 µs, 1 = mark→space, bit toggle rộng gấp đôi. */
    fun rc6(address: Int, command: Int, toggle: Boolean = false): IrSignal {
        val t = 444
        val b = PatternBuilder().mark(2666).space(889)
        fun bit(v: Int, w: Int = t) { if (v == 1) { b.mark(w); b.space(w) } else { b.space(w); b.mark(w) } }
        bit(1)                       // start
        repeat(3) { bit(0) }         // mode 000
        bit(if (toggle) 1 else 0, 2 * t)
        for (i in 7 downTo 0) bit((address shr i) and 1)
        for (i in 7 downTo 0) bit((command shr i) and 1)
        b.space(2666)
        return b.build(36_000)
    }

    /** Sony SIRC 12/15/20 bit, 40 kHz, lặp 3 khung chu kỳ 45 ms. */
    fun sirc(address: Int, command: Int, bits: Int = 12): IrSignal {
        require(bits == 12 || bits == 15 || bits == 20) { "SIRC chỉ hỗ trợ 12/15/20 bit" }
        val addrBits = bits - 7
        val v = (command and 0x7F).toLong() or ((address.toLong() and ((1L shl addrBits) - 1)) shl 7)
        val b = PatternBuilder()
        repeat(3) {
            val start = b.size
            b.mark(2400); b.space(600)
            for (i in 0 until bits) { b.mark(if ((v shr i) and 1L == 1L) 1200 else 600); b.space(600) }
            b.padTo(start, 45_000)
        }
        return b.build(40_000)
    }

    /** Kaseikyo 48 bit (Panasonic, Denon, JVC mới...): mã nhà SX 16 bit + 3 byte dữ liệu + byte kiểm tra XOR. */
    fun kaseikyo(vendor: Int, d0: Int, d1: Int, d2: Int): IrSignal {
        val bytes = intArrayOf(vendor and 0xFF, (vendor shr 8) and 0xFF, d0 and 0xFF, d1 and 0xFF, d2 and 0xFF, (d0 xor d1 xor d2) and 0xFF)
        val u = 432
        val b = PatternBuilder().mark(8 * u).space(4 * u)
        for (byte in bytes) for (i in 0 until 8) { b.mark(u); b.space(if ((byte shr i) and 1 == 1) 3 * u else u) }
        b.mark(u); b.space(173 * u)
        return b.build(37_000)
    }

    /** Panasonic = Kaseikyo với mã nhà SX 0x2002. */
    fun panasonic(device: Int, subdevice: Int, function: Int) = kaseikyo(0x2002, device, subdevice, function)

    /** Pronto Hex (dạng learned 0000). Phát phần "once" rồi phần "repeat" một lần. */
    fun pronto(text: String): IrSignal {
        val w = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt(16) }
        require(w.size >= 4 && w[0] == 0) { "Chỉ hỗ trợ Pronto dạng 0000 (raw)" }
        val freq = (1_000_000.0 / (w[1] * 0.241246)).roundToInt()
        val pairs = w[2] + w[3]
        require(w.size >= 4 + pairs * 2) { "Pronto thiếu dữ liệu" }
        val period = 1_000_000.0 / freq
        val b = PatternBuilder()
        for (i in 0 until pairs * 2) { val us = (w[4 + i] * period).roundToInt(); if (i % 2 == 0) b.mark(us) else b.space(us) }
        return b.build(freq)
    }

    /** Raw: danh sách µs mark/space xen kẽ (dấu âm được bỏ qua). */
    fun raw(frequency: Int, data: String): IrSignal {
        val nums = data.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { kotlin.math.abs(it.toInt()) }
        val b = PatternBuilder()
        nums.forEachIndexed { i, us -> if (i % 2 == 0) b.mark(us) else b.space(us) }
        return b.build(if (frequency > 0) frequency else 38_000)
    }

    val PROTOCOLS = listOf("NEC", "NECext", "Samsung32", "RC5", "RC5X", "RC6", "SIRC", "SIRC15", "SIRC20", "Kaseikyo", "Panasonic", "JVC", "PRONTO", "RAW")

    /** Dựng tín hiệu từ mô tả nút (protocol + address + command hoặc dữ liệu thô). */
    fun build(protocol: String, address: Long, command: Long, frequency: Int, data: String): IrSignal {
        val a = address.toInt(); val c = command.toInt()
        return when (protocol.uppercase()) {
            "NEC", "NEC42" -> if (a > 0xFF) necExt(a, (c and 0xFF) or ((c.inv() and 0xFF) shl 8)) else nec(a, c)
            "NECEXT" -> necExt(a, c)
            "SAMSUNG32", "SAMSUNG" -> samsung32(a, c)
            "RC5", "RC5X" -> rc5(a, c)
            "RC6" -> rc6(a, c)
            "SIRC" -> sirc(a, c, 12)
            "SIRC15" -> sirc(a, c, 15)
            "SIRC20" -> sirc(a, c, 20)
            // Flipper Kaseikyo: address = 4 byte LE [vendorLo, vendorHi, d0, d1], command = d2 (best-effort)
            "KASEIKYO" -> kaseikyo(a and 0xFFFF, (a shr 16) and 0xFF, (a shr 24) and 0xFF, c)
            "PANASONIC" -> panasonic(a and 0xFF, (a shr 8) and 0xFF, c)
            "JVC" -> jvc(a, c)
            "PRONTO" -> pronto(data)
            "RAW" -> raw(frequency, data)
            else -> throw IllegalArgumentException("Giao thức chưa hỗ trợ: $protocol")
        }
    }
}
