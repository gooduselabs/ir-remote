package vn.irremote.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.irremote.App
import vn.irremote.ac.AcMode
import vn.irremote.ac.AcResolver
import vn.irremote.ac.AcState
import vn.irremote.data.*
import vn.irremote.ir.FlipperIr
import vn.irremote.ir.IrCode
import vn.irremote.voice.ButtonKeys
import vn.irremote.voice.VAction
import vn.irremote.voice.VoiceParser
import vn.irremote.widget.FavWidget

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as App
    private val dao = a.db.dao()
    val catalog = a.catalog
    val irAvailable = a.ir.available
    val irFreq = a.ir.frequencies()

    val rooms = dao.rooms().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val remotes = dao.remotes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val macros = dao.macros().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun remote(id: Long) = dao.remote(id)
    fun buttons(id: Long) = dao.buttons(id)
    private fun toast(s: String?) { if (s != null) messages.tryEmit(s) }

    val tick = MutableStateFlow(0L)
    val lastAcSent = MutableStateFlow<String?>(null)
    fun send(b: ButtonEntity) { tick.value = System.nanoTime(); toast(a.sendButton(b)) }
    /** Nút nguồn nhanh trên thẻ thiết bị ở màn hình chính. */
    fun quickPower(r: RemoteEntity) = viewModelScope.launch {
        val btns = dao.buttonsNow(r.id)
        if (r.deviceType == DeviceType.AC.name) { val s = acState(r); applyAc(r, s.copy(power = !s.power), btns); return@launch }
        val n = ButtonKeys.find(VAction.POWER, btns.map { it.label })
        btns.firstOrNull { it.label == n }?.let { send(it) } ?: toast("Remote này chưa có nút nguồn")
    }
    fun sendCode(c: IrCode) { tick.value = System.nanoTime(); toast(runCatching { a.ir.send(c.toSignal()) }.getOrElse { "Mã lỗi: ${it.message}" }) }

    // ---- Remote & nút ----
    suspend fun saveRemote(name: String, type: DeviceType, brand: String, codes: List<IrCode>, source: String): Long {
        val id = dao.upsertRemote(RemoteEntity(name = name, deviceType = type.name, brand = brand, source = source))
        dao.upsertButtons(codes.mapIndexed { i, c -> ButtonEntity(remoteId = id, label = c.name, protocol = c.protocol, address = c.address, command = c.command, frequency = c.frequency, data = c.data, position = i) })
        return id
    }
    fun updateRemote(r: RemoteEntity) = viewModelScope.launch { dao.upsertRemote(r) }
    fun deleteRemote(id: Long) = viewModelScope.launch { dao.deleteButtonsOf(id); dao.deleteRemote(id); refreshWidget() }
    fun saveButton(b: ButtonEntity) = viewModelScope.launch { dao.upsertButton(b); refreshWidget() }
    fun deleteButton(b: ButtonEntity) = viewModelScope.launch { dao.deleteButton(b); refreshWidget() }
    fun toggleFavButton(b: ButtonEntity) = saveButton(b.copy(favorite = !b.favorite))
    private suspend fun refreshWidget() = runCatching { FavWidget().updateAll(getApplication()) }

    // ---- Phòng ----
    fun addRoom(n: String) = viewModelScope.launch { if (n.isNotBlank()) dao.upsertRoom(RoomEntity(name = n.trim())) }
    fun deleteRoom(r: RoomEntity) = viewModelScope.launch { dao.clearRoom(r.id); dao.deleteRoom(r) }

    // ---- Điều hoà ----
    fun acState(r: RemoteEntity): AcState = runCatching { App.json.decodeFromString<AcState>(r.acState) }.getOrDefault(AcState())
    fun applyAc(r: RemoteEntity, s: AcState, buttons: List<ButtonEntity>) {
        viewModelScope.launch { dao.upsertRemote(r.copy(acState = App.json.encodeToString(AcState.serializer(), s))) }
        val name = AcResolver.resolve(s, buttons.map { it.label })
        val b = buttons.firstOrNull { it.label == name }
        if (b == null) toast("Không có mã phù hợp cho trạng thái này. Hãy thêm/học nút.") else { send(b); lastAcSent.value = b.label }
    }

    // ---- Cảnh / macro ----
    fun saveMacro(m: MacroEntity) = viewModelScope.launch { dao.upsertMacro(m) }
    fun deleteMacro(m: MacroEntity) = viewModelScope.launch { dao.deleteMacro(m) }
    fun runMacro(m: MacroEntity) = viewModelScope.launch { toast(a.runMacro(m) ?: "Đã chạy cảnh \"${m.name}\"") }
    suspend fun allButtons() = dao.allButtons()

    // ---- Nhập .ir / sao lưu ----
    fun importIr(fileName: String, text: String) = viewModelScope.launch {
        val codes = FlipperIr.parse(text)
        if (codes.isEmpty()) { toast("File không có mã IR hợp lệ"); return@launch }
        val looksAc = codes.any { AcResolver.parseMode(it.name.lowercase().split('_')) != null }
        saveRemote(fileName.removeSuffix(".ir"), if (looksAc) DeviceType.AC else DeviceType.OTHER, "", codes, "import")
        toast("Đã nhập ${codes.size} nút")
    }
    suspend fun exportIr(remoteId: Long) = FlipperIr.write(dao.buttonsNow(remoteId).map { IrCode(it.label, it.protocol, it.address, it.command, it.frequency, it.data) })
    suspend fun backupJson(): String = App.json.encodeToString(Backup.serializer(), Backup(rooms = dao.roomsNow(), remotes = dao.remotesNow(), buttons = dao.allButtons(), macros = dao.macrosNow()))
    fun restore(text: String) = viewModelScope.launch {
        runCatching { dao.restore(App.json.decodeFromString(Backup.serializer(), text)) }
            .onSuccess { toast("Khôi phục thành công"); refreshWidget() }.onFailure { toast("File sao lưu không hợp lệ: ${it.message}") }
    }

    // ---- Giọng nói ----
    fun voice(text: String) = viewModelScope.launch {
        val v = VoiceParser.parse(text)
        if (v.action == VAction.SCENE) {
            val m = macros.value.firstOrNull { VoiceParser.norm(it.name).contains(v.arg.orEmpty()) || v.arg.orEmpty().contains(VoiceParser.norm(it.name)) }
            if (m != null) runMacro(m) else toast("Không tìm thấy cảnh \"${v.arg}\""); return@launch
        }
        val nt = VoiceParser.norm(text)
        val target = remotes.value.firstOrNull { nt.contains(VoiceParser.norm(it.name)) }
            ?: remotes.value.firstOrNull { it.deviceType == v.deviceHint }
            ?: remotes.value.firstOrNull { it.favorite } ?: remotes.value.firstOrNull()
        if (target == null) { toast("Chưa có thiết bị nào"); return@launch }
        val btns = withContext(Dispatchers.IO) { dao.buttonsNow(target.id) }
        if (target.deviceType == DeviceType.AC.name && v.action in listOf(VAction.AC_TEMP, VAction.AC_MODE, VAction.POWER_ON, VAction.POWER_OFF)) {
            var s = acState(target)
            s = when (v.action) {
                VAction.AC_TEMP -> s.copy(power = true).withTemp(v.number!!)
                VAction.AC_MODE -> s.copy(power = true, mode = AcMode.valueOf(v.arg!!))
                VAction.POWER_ON -> s.copy(power = true)
                else -> s.copy(power = false)
            }
            applyAc(target, s, btns); return@launch
        }
        val names = btns.map { it.label }
        val label: List<String?> = when (v.action) {
            VAction.CHANNEL -> v.number.toString().map { ButtonKeys.digit(it, names) }
            VAction.UNKNOWN, VAction.AC_TEMP, VAction.AC_MODE -> listOf(null)
            else -> listOf(ButtonKeys.find(v.action, names))
        }
        if (label.any { it == null }) { toast("Chưa hiểu lệnh: \"$text\""); return@launch }
        for (l in label) { btns.first { it.label == l }.let { send(it) }; kotlinx.coroutines.delay(350) }
        toast("${target.name}: ${label.joinToString()}")
    }
}
