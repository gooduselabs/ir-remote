package vn.irremote

import android.app.Application
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import vn.irremote.data.*
import vn.irremote.hw.IrTransmitter
import vn.irremote.ir.Encoders

/** Service locator đơn giản (không dùng Hilt để giữ build gọn). */
class App : Application() {
    lateinit var db: AppDb; lateinit var ir: IrTransmitter; lateinit var catalog: Catalog
    override fun onCreate() {
        super.onCreate(); instance = this
        db = AppDb.create(this); ir = IrTransmitter(this)
        catalog = Catalog { p -> assets.open(p).bufferedReader().use { it.readText() } }
    }
    fun sendButton(b: ButtonEntity): String? =
        runCatching { Encoders.build(b.protocol, b.address, b.command, b.frequency, b.data) }
            .fold({ ir.send(it) }, { "Mã không hợp lệ: ${it.message}" })

    suspend fun runMacro(m: MacroEntity): String? {
        val steps = json.decodeFromString<List<MacroStep>>(m.steps)
        for (s in steps) {
            val b = db.dao().button(s.buttonId) ?: continue
            sendButton(b)?.let { return it }
            delay(s.delayMs)
        }
        return null
    }
    companion object {
        lateinit var instance: App
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
