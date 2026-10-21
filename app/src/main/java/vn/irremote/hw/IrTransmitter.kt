package vn.irremote.hw

import android.content.Context
import android.hardware.ConsumerIrManager
import vn.irremote.ir.IrSignal

/** Bọc ConsumerIrManager; trả về thông báo lỗi tiếng Việt thay vì crash. */
class IrTransmitter(context: Context) {
    private val mgr = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    val available: Boolean get() = runCatching { mgr?.hasIrEmitter() == true }.getOrDefault(false)

    fun frequencies(): String = runCatching {
        mgr?.carrierFrequencies?.joinToString { "${it.minFrequency / 1000}–${it.maxFrequency / 1000} kHz" }
    }.getOrNull() ?: "không rõ"

    /** @return null nếu thành công, hoặc thông báo lỗi. */
    fun send(sig: IrSignal): String? {
        if (!available) return NO_IR
        if (sig.totalMicros > 2_000_000) return "Tín hiệu quá dài (>2 giây), Android không cho phát."
        return runCatching { mgr!!.transmit(sig.frequency, sig.pattern); null }.getOrElse { "Lỗi phát IR: ${it.message}" }
    }
    companion object { const val NO_IR = "Điện thoại này không có cổng phát hồng ngoại (IR blaster). Bạn vẫn có thể xem, sửa và sao lưu remote." }
}
