package vn.irremote

import org.junit.Assert.*
import org.junit.Test
import vn.irremote.ir.Encoders
import vn.irremote.ir.IrSignal

class EncodersTest {
    /** Giải mã pulse-distance (LSB trước) để kiểm tra ngược. */
    private fun decodePd(p: IntArray, start: Int, bits: Int, threshold: Int = 1100): Long {
        var v = 0L
        for (i in 0 until bits) if (p[start + 2 * i + 1] > threshold) v = v or (1L shl i)
        return v
    }
    /** Mở rộng thành chuỗi mức nửa-bit (true = mark) với đơn vị [t]. */
    private fun halves(p: IntArray, t: Int, skip: Int = 0): List<Boolean> {
        val out = ArrayList<Boolean>()
        for (i in skip until p.size) repeat(Math.round(p[i].toFloat() / t)) { out += (i % 2 == 0) }
        return out
    }

    @Test fun nec() {
        val s = Encoders.nec(0x04, 0x08)
        assertEquals(38000, s.frequency)
        assertEquals(9000, s.pattern[0]); assertEquals(4500, s.pattern[1])
        assertEquals(0xF708FB04L, decodePd(s.pattern, 2, 32))
        assertEquals(108_000L, s.totalMicros)
    }
    @Test fun necExt() {
        val s = Encoders.necExt(0xBF40, 0xFD02)
        assertEquals(0xFD02BF40L, decodePd(s.pattern, 2, 32))
    }
    @Test fun samsung32() {
        val s = Encoders.samsung32(0x07, 0x02)
        assertEquals(4500, s.pattern[0]); assertEquals(4500, s.pattern[1])
        assertEquals(0xFD020707L, decodePd(s.pattern, 2, 32))
    }
    @Test fun jvc() {
        val s = Encoders.jvc(0x03, 0x17)
        assertEquals(8400, s.pattern[0]); assertEquals(0x1703L, decodePd(s.pattern, 2, 16))
    }
    @Test fun rc5() {
        val s = Encoders.rc5(0x05, 0x23)
        assertEquals(36000, s.frequency)
        // bit đầu là 1 → space bị bỏ, thêm lại 1 nửa-bit space để giải mã
        val h = listOf(false) + halves(s.pattern, 889)
        val bits = (0 until 14).map { if (!h[2 * it] && h[2 * it + 1]) 1 else 0 }
        assertEquals(listOf(1, 1, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, 1, 1), bits)
    }
    @Test fun rc5x_clearsS2() {
        val h = listOf(false) + halves(Encoders.rc5(0x05, 0x43).pattern, 889)
        assertFalse("S2 phải = 0 khi lệnh >= 64", !h[2] && h[3])
    }
    @Test fun rc6() {
        val s = Encoders.rc6(0x00, 0x0C)
        assertEquals(2666, s.pattern[0]); assertEquals(36000, s.frequency)
        // sau leader: nửa-bit 444; start(1)=10, mode 000 = 01 01 01, toggle 0 = 0011
        val h = halves(s.pattern, 444).drop(6 + 2) // 2666≈6 nửa-bit mark, 889≈2 space
        val head = h.take(12).map { if (it) 1 else 0 }
        assertEquals(listOf(1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1), head)
        val data = h.drop(12).take(32).chunked(2).map { if (it[0]) 1 else 0 }
        assertEquals("0000000000001100", data.joinToString(""))
    }
    @Test fun sirc() {
        val s = Encoders.sirc(0x01, 0x15, 12)
        assertEquals(40000, s.frequency); assertEquals(135_000L, s.totalMicros)
        assertEquals(2400, s.pattern[0])
        var v = 0; for (i in 0 until 12) if (s.pattern[2 + 2 * i] > 900) v = v or (1 shl i)
        assertEquals(0x15 or (0x01 shl 7), v)
        val s20 = Encoders.sirc(0x1A5, 0x15, 20); assertEquals(3 * (2 + 40), s20.pattern.size)
    }
    @Test fun kaseikyo() {
        val s = Encoders.panasonic(0x80, 0x00, 0x3D)
        assertEquals(37000, s.frequency); assertEquals(3456, s.pattern[0])
        assertEquals(2 + 96 + 2, s.pattern.size)
        val bytes = (0 until 6).map { decodePd(s.pattern, 2 + 16 * it, 8, 864).toInt() }
        assertEquals(listOf(0x02, 0x20, 0x80, 0x00, 0x3D, 0x80 xor 0x3D), bytes)
    }
    @Test fun pronto() {
        val s = Encoders.pronto("0000 006D 0002 0000 0010 0020 0030 0040")
        assertTrue(s.frequency in 37900..38100)
        assertArrayEquals(intArrayOf(421, 841, 1262, 1683), s.pattern)
    }
    @Test fun rawMergesAndKeepsFreq() {
        val s: IrSignal = Encoders.raw(36000, "100 200 -300 400")
        assertEquals(36000, s.frequency); assertArrayEquals(intArrayOf(100, 200, 300, 400), s.pattern)
    }
    @Test fun buildDispatch() {
        for (p in listOf("NEC", "NECext", "Samsung32", "RC5", "RC5X", "RC6", "SIRC", "SIRC15", "SIRC20", "Kaseikyo", "Panasonic", "JVC"))
            assertTrue(p, Encoders.build(p, 1, 2, 38000, "").pattern.isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) { Encoders.build("XYZ", 0, 0, 0, "") }
    }
}
