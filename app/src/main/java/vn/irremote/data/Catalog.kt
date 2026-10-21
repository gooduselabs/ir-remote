package vn.irremote.data

import vn.irremote.ir.FlipperIr
import vn.irremote.ir.IrCode

/** Một bộ mã trong kho kèm app (một file .ir của Flipper-IRDB). */
data class CatalogEntry(val path: String, val type: DeviceType, val brand: String, val model: String)

/** Kho mã khởi đầu đọc từ assets/irdb (Flipper-IRDB, CC0-1.0, chỉ các file thêm sau commit 2319685). */
class Catalog(private val open: (String) -> String) {
    val entries: List<CatalogEntry> by lazy {
        open("irdb/index.txt").lines().filter { it.endsWith(".ir") }.map { p ->
            val parts = p.split('/')
            CatalogEntry(p, typeOf(parts[0]), parts.getOrElse(1) { "?" }.replace('_', ' '), parts.last().removeSuffix(".ir").replace('_', ' '))
        }
    }
    fun brands(type: DeviceType) = entries.filter { it.type == type }.map { it.brand }.distinct().sorted()
    fun candidates(type: DeviceType, brand: String?) = entries.filter { it.type == type && (brand == null || it.brand == brand) }
    fun load(e: CatalogEntry): List<IrCode> = FlipperIr.parse(open("irdb/" + e.path))

    companion object {
        fun typeOf(folder: String) = when (folder) {
            "TVs" -> DeviceType.TV; "Cable_Boxes", "DVB-T", "TV_Tuner", "Streaming_Devices" -> DeviceType.STB
            "Fans" -> DeviceType.FAN; "ACs" -> DeviceType.AC; "Projectors" -> DeviceType.PROJECTOR; else -> DeviceType.OTHER
        }
    }
}
