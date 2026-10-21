package vn.irremote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import vn.irremote.ac.AcFan
import vn.irremote.ac.AcMode
import vn.irremote.data.ButtonEntity
import vn.irremote.data.DeviceType
import vn.irremote.data.RemoteEntity
import vn.irremote.ir.Encoders
import vn.irremote.ir.FlipperIr

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RemoteScreen(vm: MainViewModel, id: Long, nav: NavController) {
    val remote by vm.remote(id).collectAsState(null)
    val buttons by vm.buttons(id).collectAsState(emptyList())
    val r = remote ?: return
    val host = remember { SnackbarHostState() }; Messages(vm, host)
    val haptic = LocalHapticFeedback.current
    var editing by remember { mutableStateOf<ButtonEntity?>(null) }
    var menuFor by remember { mutableStateOf<ButtonEntity?>(null) }
    var settings by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val t = vm.exportIr(id); ctx.contentResolver.openOutputStream(uri)?.use { it.write(t.toByteArray()) }; vm.messages.tryEmit("Đã xuất file .ir") }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(r.name) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            actions = {
                IconButton(onClick = { vm.updateRemote(r.copy(favorite = !r.favorite)) }) { Icon(if (r.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Yêu thích") }
                IconButton(onClick = { editing = ButtonEntity(remoteId = id, label = "", protocol = "NEC", position = buttons.size) }) { Icon(Icons.Default.Add, "Thêm nút") }
                IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, "Thêm") }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text("Sửa thông tin / phòng") }, onClick = { more = false; settings = true })
                    DropdownMenuItem(text = { Text("Xuất file .ir") }, onClick = { more = false; export.launch(r.name + ".ir") })
                    DropdownMenuItem(text = { Text("Xoá remote") }, onClick = { more = false; vm.deleteRemote(id); nav.popBackStack() })
                }
            }) },
        snackbarHost = { SnackbarHost(host) },
    ) { pad ->
        LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.padding(pad), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.deviceType == DeviceType.AC.name) item(span = { GridItemSpan(maxLineSpan) }) { AcPanel(vm, r, buttons) }
            if (buttons.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Chưa có nút. Bấm + để thêm nút (nhập mã NEC/RC5/... hoặc Pronto/raw).") }
            items(buttons, key = { it.id }) { b ->
                Box {
                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp,
                        modifier = Modifier.height(64.dp).fillMaxWidth().combinedClickable(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.send(b) },
                            onLongClick = { menuFor = b })) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(b.label.replace('_', ' '), textAlign = TextAlign.Center, fontSize = 14.sp, maxLines = 2)
                            if (b.favorite) Icon(Icons.Default.Star, null, Modifier.align(Alignment.TopEnd).padding(4.dp).size(12.dp))
                        }
                    }
                    DropdownMenu(menuFor == b, { menuFor = null }) {
                        DropdownMenuItem(text = { Text("Sửa nút") }, onClick = { menuFor = null; editing = b })
                        DropdownMenuItem(text = { Text(if (b.favorite) "Bỏ khỏi widget/ô nhanh" else "Thêm vào widget/ô nhanh") }, onClick = { menuFor = null; vm.toggleFavButton(b) })
                        DropdownMenuItem(text = { Text("Xoá nút") }, onClick = { menuFor = null; vm.deleteButton(b) })
                    }
                }
            }
        }
    }
    editing?.let { b -> ButtonEditor(b, onTest = { vm.send(it) }, onSave = { vm.saveButton(it); editing = null }, onDismiss = { editing = null }) }
    if (settings) RemoteSettings(vm, r) { settings = false }
}

@Composable
fun AcPanel(vm: MainViewModel, r: RemoteEntity, buttons: List<ButtonEntity>) {
    var s by remember(r.id) { mutableStateOf(vm.acState(r)) }
    fun apply(n: vn.irremote.ac.AcState) { s = n; vm.applyAc(r, n, buttons) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (s.power) "${s.temp}°C" else "TẮT", fontSize = 48.sp)
            Text(if (s.power) "${s.mode.label} · Quạt ${s.fan.label}" else "Trạng thái được ghi nhớ trên máy")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { apply(s.copy(power = true).withTemp(s.temp - 1)) }) { Icon(Icons.Default.Remove, "Giảm") }
                Button(onClick = { apply(s.copy(power = !s.power)) }, Modifier.padding(horizontal = 16.dp)) { Icon(Icons.Default.PowerSettingsNew, null); Text(if (s.power) " Tắt" else " Bật") }
                FilledTonalIconButton(onClick = { apply(s.copy(power = true).withTemp(s.temp + 1)) }) { Icon(Icons.Default.Add, "Tăng") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { AcMode.entries.forEach { m -> FilterChip(s.mode == m, { apply(s.copy(power = true, mode = m)) }, { Text(m.label, fontSize = 11.sp) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { AcFan.entries.forEach { f -> FilterChip(s.fan == f, { apply(s.copy(power = true, fan = f)) }, { Text("Quạt " + f.label, fontSize = 11.sp) }) } }
        }
    }
}

@Composable
fun ButtonEditor(initial: ButtonEntity, onTest: (ButtonEntity) -> Unit, onSave: (ButtonEntity) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(initial.label) }
    var proto by remember { mutableStateOf(initial.protocol) }
    var addr by remember { mutableStateOf("%X".format(initial.address)) }
    var cmd by remember { mutableStateOf("%X".format(initial.command)) }
    var freq by remember { mutableStateOf(initial.frequency.toString()) }
    var data by remember { mutableStateOf(initial.data) }
    var protoMenu by remember { mutableStateOf(false) }
    val isData = proto == "RAW" || proto == "PRONTO"
    fun current() = initial.copy(label = label.ifBlank { "Nút" }, protocol = proto, address = addr.toLongOrNull(16) ?: 0,
        command = cmd.toLongOrNull(16) ?: 0, frequency = freq.toIntOrNull() ?: 38000, data = data.trim())
    val err = remember(proto, addr, cmd, freq, data) { runCatching { val c = current(); Encoders.build(c.protocol, c.address, c.command, c.frequency, c.data) }.exceptionOrNull()?.message }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.id == 0L) "Thêm nút" else "Sửa nút") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(label, { label = it }, label = { Text("Tên nút (vd: Power, Vol_up)") }, singleLine = true)
            Box { OutlinedButton(onClick = { protoMenu = true }) { Text("Giao thức: $proto") }
                DropdownMenu(protoMenu, { protoMenu = false }) { Encoders.PROTOCOLS.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { proto = p; protoMenu = false }) } } }
            if (isData) {
                if (proto == "RAW") OutlinedTextField(freq, { freq = it }, label = { Text("Tần số (Hz)") }, singleLine = true)
                OutlinedTextField(data, { data = it }, label = { Text(if (proto == "RAW") "Xung µs (mark space ...)" else "Pronto Hex (0000 006D ...)") }, minLines = 3, maxLines = 6)
            } else {
                OutlinedTextField(addr, { addr = it }, label = { Text("Địa chỉ (hex)") }, singleLine = true)
                OutlinedTextField(cmd, { cmd = it }, label = { Text("Lệnh (hex)") }, singleLine = true)
                Text("Định dạng Flipper: \"07 00 00 00\" ⇒ nhập 7", fontSize = 11.sp)
            }
            if (err != null) Text("⚠ $err", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(enabled = err == null, onClick = { onSave(current()) }) { Text("Lưu") } },
        dismissButton = { Row { TextButton(enabled = err == null, onClick = { onTest(current()) }) { Text("Thử") }; TextButton(onClick = onDismiss) { Text("Huỷ") } } })
}

@Composable
fun RemoteSettings(vm: MainViewModel, r: RemoteEntity, onDismiss: () -> Unit) {
    val rooms by vm.rooms.collectAsState()
    var name by remember { mutableStateOf(r.name) }
    var type by remember { mutableStateOf(typeOf(r.deviceType)) }
    var room by remember { mutableStateOf(r.roomId) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Thông tin remote") }, text = { Column {
        OutlinedTextField(name, { name = it }, label = { Text("Tên") }, singleLine = true)
        Text("Loại thiết bị", Modifier.padding(top = 8.dp))
        FlowRowChips(DeviceType.entries.map { it.label }, DeviceType.entries.indexOf(type)) { type = DeviceType.entries[it] }
        Text("Phòng", Modifier.padding(top = 8.dp))
        FlowRowChips(listOf("Không") + rooms.map { it.name }, if (room == null) 0 else rooms.indexOfFirst { it.id == room } + 1) { room = if (it == 0) null else rooms[it - 1].id }
    } }, confirmButton = { TextButton(onClick = { vm.updateRemote(r.copy(name = name, deviceType = type.name, roomId = room)); onDismiss() }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o -> FilterChip(i == selected, { onSelect(i) }, { Text(o) }) }
    }
}
