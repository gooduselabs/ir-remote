package vn.irremote

import org.junit.Assert.*
import org.junit.Test
import vn.irremote.ac.*
import vn.irremote.data.Catalog
import vn.irremote.ir.FlipperIr
import vn.irremote.voice.*
import java.io.File

class ParsersTest {
    private val sample = """
        Filetype: IR signals file
        Version: 1
        # 
        name: Power
        type: parsed
        protocol: NECext
        address: 40 BF 00 00
        command: 02 FD 00 00
        # 
        name: Off
        type: raw
        frequency: 38000
        duty_cycle: 0.330000
        data: 3445 1725 427 1295
    """.trimIndent()

    @Test fun parseIr() {
        val c = FlipperIr.parse(sample)
        assertEquals(2, c.size)
        assertEquals(0xBF40L, c[0].address); assertEquals(0xFD02L, c[0].command); assertEquals("NECext", c[0].protocol)
        assertEquals("RAW", c[1].protocol); assertEquals(4, c[1].toSignal().pattern.size)
    }
    @Test fun roundTrip() {
        val c = FlipperIr.parse(sample)
        assertEquals(c, FlipperIr.parse(FlipperIr.write(c)))
    }

    /** Mọi file trong kho kèm app phải đọc được và mã hoá được. */
    @Test fun bundledCatalogAllEncodes() {
        val root = File("src/main/assets")
        val cat = Catalog { File(root, it).readText() }
        assertTrue(cat.entries.size >= 40)
        var n = 0
        for (e in cat.entries) for (code in cat.load(e)) { code.toSignal(); n++ }
        assertTrue("đã mã hoá $n nút", n > 500)
    }

    @Test fun acResolver() {
        val names = listOf("Off", "Cool_25_slow", "Cool_25_auto", "Cool_18_auto", "Heat_28_auto", "Dry_25_auto")
        assertEquals("Off", AcResolver.resolve(AcState(power = false), names))
        assertEquals("Cool_25_auto", AcResolver.resolve(AcState(true, AcMode.COOL, 25, AcFan.AUTO), names))
        assertEquals("Cool_25_slow", AcResolver.resolve(AcState(true, AcMode.COOL, 25, AcFan.LOW), names))
        assertEquals("Cool_18_auto", AcResolver.resolve(AcState(true, AcMode.COOL, 19, AcFan.AUTO), names))
        assertEquals("Heat_28_auto", AcResolver.resolve(AcState(true, AcMode.HEAT, 26), names))
        assertEquals("Dry_25_auto", AcResolver.resolve(AcState(true, AcMode.DRY, 25), names))
        assertEquals(30, AcState().withTemp(30).temp); assertEquals(16, AcState().withTemp(5).temp)
    }

    @Test fun voice() {
        assertEquals(VoiceIntent(VAction.POWER_OFF, "TV"), VoiceParser.parse("Tắt tivi"))
        assertEquals(VAction.VOL_UP, VoiceParser.parse("tăng âm lượng").action)
        assertEquals(VAction.MUTE, VoiceParser.parse("tắt tiếng").action)
        assertEquals(VoiceIntent(VAction.AC_TEMP, "AC", 25), VoiceParser.parse("Điều hoà 25 độ"))
        assertEquals(VoiceIntent(VAction.CHANNEL, null, 12), VoiceParser.parse("kênh 12"))
        assertEquals("COOL", VoiceParser.parse("bật chế độ lạnh").arg)
        assertEquals(VoiceIntent(VAction.SCENE, arg = "xem phim"), VoiceParser.parse("Chạy cảnh xem phim"))
        assertEquals(VoiceIntent(VAction.POWER_ON, "FAN"), VoiceParser.parse("bật quạt"))
        assertEquals(VAction.UNKNOWN, VoiceParser.parse("xin chào").action)
    }
    @Test fun buttonKeys() {
        val n = listOf("Power", "Vol_up", "Vol_dn", "Ch_next", "Mute", "1", "2")
        assertEquals("Vol_dn", ButtonKeys.find(VAction.VOL_DOWN, n))
        assertEquals("Ch_next", ButtonKeys.find(VAction.CH_UP, n))
        assertEquals("2", ButtonKeys.digit('2', n))
        assertEquals("Power", ButtonKeys.testCandidate(n))
    }
}
