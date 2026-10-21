package vn.irremote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import vn.irremote.ac.AcFan
import vn.irremote.ac.AcMode
import vn.irremote.ac.AcState
import vn.irremote.data.ButtonEntity
import vn.irremote.data.DeviceType
import vn.irremote.data.RemoteEntity
import vn.irremote.ir.Encoders
import vn.irremote.ir.RemoteLayout
import vn.irremote.ir.Slot
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(vm: MainViewModel, id: Long, nav: NavController) {
    val remote by vm.remote(id).collectAsState(null)
    val buttons by vm.buttons(id).collectAsState(emptyList())
    val r = remote ?: return
    val host = remember { SnackbarHostState() }; Messages(vm, host)
    val tick by vm.tick.collectAsState(); val lastAc by vm.lastAcSent.collectAsState()
    var editing by remember { mutableStateOf<ButtonEntity?>(null) }
    var sheetFor by remember { mutableStateOf<ButtonEntity?>(null) }
    var settings by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val t = vm.exportIr(id); ctx.contentResolver.openOutputStream(uri)?.use { it.write(t.toByteArray()) }; vm.messages.tryEmit("Đã xuất file .ir") }
    }
    val type = typeOf(r.deviceType)
    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text(r.name, style = MaterialTheme.typography.titleLarge)
                Text(listOf(type.label, r.brand).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            actions = {
                IrLed(tick)
                IconButton(onClick = { vm.updateRemote(r.copy(favorite = !r.favorite)) }) { Icon(if (r.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Yêu thích", tint = if (r.favorite) Brand.Warm else LocalContentColor.current) }
                IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, "Thêm") }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Add, null) }, text = { Text("Thêm nút") }, onClick = { more = false; editing = ButtonEntity(remoteId = id, label = "", protocol = "NEC", position = buttons.size) })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Edit, null) }, text = { Text("Sửa thông tin / phòng") }, onClick = { more = false; settings = true })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.IosShare, null) }, text = { Text("Xuất file .ir") }, onClick = { more = false; export.launch(r.name + ".ir") })
                    DropdownMenuItem(leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, text = { Text("Xoá remote") }, onClick = { more = false; vm.deleteRemote(id); nav.popBackStack() })
                }
            }) },
        snackbarHost = { SnackbarHost(host) },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (type == DeviceType.AC) {
                var s by remember(r.id) { mutableStateOf(vm.acState(r)) }
                AcContent(s, lastAc, buttons, onChange = { s = it; vm.applyAc(r, it, buttons) }, onSend = vm::send, onLong = { sheetFor = it })
            } else RemoteContent(buttons, onSend = vm::send, onLong = { sheetFor = it }, onAdd = { editing = ButtonEntity(remoteId = id, label = "", protocol = "NEC", position = buttons.size) })
        }
    }
    sheetFor?.let { b ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }) {
            Text(b.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Sp.xl))
            Text("${b.protocol} · ${if (b.protocol in listOf("RAW", "PRONTO")) "dữ liệu thô" else "addr 0x%X cmd 0x%X".format(b.address, b.command)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Sp.xl))
            ListItem(leadingContent = { Icon(Icons.Default.Edit, null) }, headlineContent = { Text("Sửa nút") }, modifier = Modifier.clickableRow { sheetFor = null; editing = b })
            ListItem(leadingContent = { Icon(if (b.favorite) Icons.Default.StarBorder else Icons.Default.Widgets, null) },
                headlineContent = { Text(if (b.favorite) "Bỏ khỏi widget / ô nhanh" else "Thêm vào widget / ô nhanh") }, modifier = Modifier.clickableRow { sheetFor = null; vm.toggleFavButton(b) })
            ListItem(leadingContent = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, headlineContent = { Text("Xoá nút", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickableRow { sheetFor = null; vm.deleteButton(b) })
            Spacer(Modifier.height(Sp.xl))
        }
    }
    editing?.let { b -> ButtonEditor(b, onTest = { vm.send(it) }, onSave = { vm.saveButton(it); editing = null }, onDismiss = { editing = null }) }
    if (settings) RemoteSettings(vm, r) { settings = false }
}

fun Modifier.clickableRow(f: () -> Unit) = this.clickable(onClick = f)

private fun ButtonEntity.pretty() = label.replace('_', ' ')

/** Remote thật: nguồn + phím chức năng, D-pad, rocker âm lượng/kênh, bàn phím số, các nút khác. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteContent(buttons: List<ButtonEntity>, onSend: (ButtonEntity) -> Unit, onLong: (ButtonEntity) -> Unit, onAdd: () -> Unit) {
    if (buttons.isEmpty()) { EmptyState(Icons.Default.TouchApp, "Remote chưa có nút", "Thêm nút bằng mã NEC, RC5, Samsung… hoặc dán Pronto/raw. Có thể nhập cả file .ir.", "Thêm nút", onAdd); return }
    val m = remember(buttons) { RemoteLayout.map(buttons) { it.label } }
    fun f(s: Slot): (() -> Unit)? = m.slots[s]?.let { b -> { onSend(b) } }
    fun lp(s: Slot): (() -> Unit)? = m.slots[s]?.let { b -> { onLong(b) } }
    val c = MaterialTheme.colorScheme
    @Composable fun Small(s: Slot, icon: ImageVector, d: String) {
        val b = m.slots[s]
        if (b != null) RemoteKey({ onSend(b) }, size = 52.dp, onLongClick = { onLong(b) }) { KeyIcon(icon, d, 22.dp) } else Spacer(Modifier.size(52.dp))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Sp.xl, vertical = Sp.l),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Sp.xl)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            m.slots[Slot.POWER]?.let { b -> RemoteKey({ onSend(b) }, size = 64.dp, container = Brand.Power, content = Color.White, glowColor = Brand.Power, onLongClick = { onLong(b) }) { KeyIcon(Icons.Default.PowerSettingsNew, "Nguồn", 30.dp) } }
                ?: Spacer(Modifier.size(64.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
                Small(Slot.SOURCE, Icons.Default.Input, "Nguồn vào"); Small(Slot.HOME, Icons.Default.Home, "Trang chủ"); Small(Slot.MENU, Icons.Default.Menu, "Menu")
            }
        }
        if (m.hasDpad || Slot.OK in m.slots) {
            DPad(f(Slot.UP), f(Slot.DOWN), f(Slot.LEFT), f(Slot.RIGHT), f(Slot.OK))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Small(Slot.BACK, Icons.AutoMirrored.Filled.KeyboardReturn, "Quay lại"); Small(Slot.INFO, Icons.Default.Info, "Thông tin")
            }
        }
        if (listOf(Slot.VOL_UP, Slot.VOL_DN, Slot.CH_UP, Slot.CH_DN, Slot.MUTE).any { it in m.slots })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Rocker("VOL", f(Slot.VOL_UP), f(Slot.VOL_DN))
                Small(Slot.MUTE, Icons.AutoMirrored.Filled.VolumeOff, "Tắt tiếng")
                Rocker("CH", f(Slot.CH_UP), f(Slot.CH_DN), Icons.Default.KeyboardArrowUp, Icons.Default.KeyboardArrowDown)
            }
        if (m.hasDigits) Column(verticalArrangement = Arrangement.spacedBy(Sp.m)) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -1)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.xl)) {
                    row.forEach { d -> val b = if (d >= 0) m.slots[Slot.valueOf("D$d")] else null
                        if (b != null) RemoteKey({ onSend(b) }, size = 60.dp, onLongClick = { onLong(b) }) { Text("$d", style = MaterialTheme.typography.headlineSmall) } else Spacer(Modifier.size(60.dp)) }
                }
            }
        }
        if (m.extras.isNotEmpty()) {
            if (m.slots.isNotEmpty()) SectionTitle("Các nút khác", Modifier.fillMaxWidth())
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sp.s, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Sp.s)) {
                m.extras.forEach { b -> RemoteKey({ onSend(b) }, shape = RoundedCornerShape(18.dp), width = 100.dp, size = 52.dp, onLongClick = { onLong(b) }) {
                    Text(b.pretty(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 6.dp)) } }
            }
        }
        Text("Nhấn giữ một phím để sửa hoặc thêm vào widget", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }
}

fun AcMode.icon(): ImageVector = when (this) { AcMode.COOL -> Icons.Default.AcUnit; AcMode.HEAT -> Icons.Default.WbSunny; AcMode.DRY -> Icons.Default.WaterDrop; AcMode.FAN -> Icons.Default.Air; AcMode.AUTO -> Icons.Default.Autorenew }

/** Vòng cung nhiệt độ: kéo để chỉnh, màu chuyển từ lạnh sang ấm. */
@Composable
fun TempDial(state: AcState, onTemp: (Int) -> Unit, onCommit: (Int) -> Unit, size: androidx.compose.ui.unit.Dp = 280.dp) {
    val c = MaterialTheme.colorScheme
    val frac by animateFloatAsState((state.temp - 16) / 16f, label = "f")
    val on = state.power
    val accent by animateColorAsState(if (!on) c.outline else if (state.mode == AcMode.HEAT) Brand.Warm else Brand.Cool, label = "a")
    var dragTemp by remember { mutableIntStateOf(state.temp) }
    Box(Modifier.size(size).pointerInput(on) {
        if (!on) return@pointerInput
        detectDragGestures(onDragEnd = { onCommit(dragTemp) }) { ch, _ ->
            val cx = this.size.width / 2f; val cy = this.size.height / 2f
            var ang = Math.toDegrees(atan2((ch.position.y - cy).toDouble(), (ch.position.x - cx).toDouble())) - 135
            while (ang < 0) ang += 360
            if (ang <= 270) { dragTemp = (16 + (ang / 270 * 16)).toInt().coerceIn(16, 32); onTemp(dragTemp) }
        }
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val stroke = 22.dp.toPx()
            val arc = Size(this.size.width - stroke, this.size.height - stroke); val tl = Offset(stroke / 2, stroke / 2)
            drawArc(c.surfaceContainerHighest, 135f, 270f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (on) {
                val mid = Color(0xFFFF7D50)
                drawArc(Brush.sweepGradient(0f to mid, 0.125f to Brand.Power, 0.25f to Brand.Power, 0.375f to Brand.Cool, 0.625f to Brand.Cyan, 0.875f to Brand.Warm, 1f to mid, center = center),
                    135f, 270f * frac.coerceAtLeast(0.01f), false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val a = Math.toRadians(135.0 + 270 * frac); val rad = arc.width / 2
                val p = Offset(center.x + (rad * cos(a)).toFloat(), center.y + (rad * sin(a)).toFloat())
                drawCircle(accent.copy(alpha = 0.35f), stroke * 0.95f, p); drawCircle(Color.White, stroke * 0.42f, p)
            }
            for (i in 0..16) { val a = Math.toRadians(135.0 + 270.0 * i / 16); val r1 = arc.width / 2 - stroke * 1.1f; val r2 = r1 - if (i % 4 == 0) 10f else 5f
                drawLine(c.onSurfaceVariant.copy(alpha = 0.4f), Offset(center.x + (r1 * cos(a)).toFloat(), center.y + (r1 * sin(a)).toFloat()), Offset(center.x + (r2 * cos(a)).toFloat(), center.y + (r2 * sin(a)).toFloat()), 2f) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (on) {
                Row(verticalAlignment = Alignment.Top) { Text("${state.temp}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp)); Text("°C", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 18.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(state.mode.icon(), null, Modifier.size(18.dp), tint = accent); Spacer(Modifier.width(6.dp))
                    Text("${state.mode.label} · Quạt ${state.fan.label.lowercase()}", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant) }
            } else {
                Text("TẮT", style = MaterialTheme.typography.displayMedium, color = c.onSurfaceVariant)
                Text("Nhấn nút nguồn để bật", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcContent(s: AcState, lastSent: String?, buttons: List<ButtonEntity>, onChange: (AcState) -> Unit, onSend: (ButtonEntity) -> Unit, onLong: (ButtonEntity) -> Unit) {
    var preview by remember(s) { mutableStateOf(s) }
    var showRaw by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Sp.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Sp.l)) {
        TempDial(preview, onTemp = { preview = preview.withTemp(it) }, onCommit = { onChange(s.withTemp(it)) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.xl)) {
            RemoteKey({ onChange(s.copy(power = true).withTemp(s.temp - 1)) }, size = 60.dp, glowColor = Brand.Cool) { KeyIcon(Icons.Default.Remove, "Giảm nhiệt độ") }
            RemoteKey({ onChange(s.copy(power = !s.power)) }, size = 76.dp, container = if (s.power) Brand.Power else MaterialTheme.colorScheme.surfaceContainerHighest,
                content = Color.White, glowColor = Brand.Power) { KeyIcon(Icons.Default.PowerSettingsNew, "Nguồn", 34.dp) }
            RemoteKey({ onChange(s.copy(power = true).withTemp(s.temp + 1)) }, size = 60.dp, glowColor = Brand.Warm) { KeyIcon(Icons.Default.Add, "Tăng nhiệt độ") }
        }
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Sp.l)) {
                SectionTitle("Chế độ")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Sp.s), verticalArrangement = Arrangement.spacedBy(Sp.s)) {
                    AcMode.entries.forEach { m -> FilterChip(s.mode == m, { onChange(s.copy(power = true, mode = m)) }, { Text(m.label) }, leadingIcon = { Icon(m.icon(), null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(50)) }
                }
                SectionTitle("Tốc độ quạt", Modifier.padding(top = Sp.s))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Sp.s), verticalArrangement = Arrangement.spacedBy(Sp.s)) {
                    AcFan.entries.forEach { f -> FilterChip(s.fan == f, { onChange(s.copy(power = true, fan = f)) }, { Text(f.label) }, leadingIcon = { Icon(Icons.Default.Air, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(50)) }
                }
            }
        }
        if (lastSent != null) AssistChip({}, { Text("Đã gửi mã: ${lastSent.replace('_', ' ')}") }, leadingIcon = { Icon(Icons.Default.Sensors, null, Modifier.size(18.dp)) })
        Text("App ghi nhớ trạng thái và chọn mã khớp nhất trong ${buttons.size} mã của điều hoà.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        TextButton({ showRaw = !showRaw }) { Text(if (showRaw) "Ẩn các mã gốc" else "Xem các mã gốc (${buttons.size})") }
        if (showRaw) FlowRow(horizontalArrangement = Arrangement.spacedBy(Sp.s), verticalArrangement = Arrangement.spacedBy(Sp.s)) {
            buttons.forEach { b -> RemoteKey({ onSend(b) }, shape = RoundedCornerShape(18.dp), width = 104.dp, size = 48.dp, onLongClick = { onLong(b) }) { Text(b.pretty(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2) } }
        }
        Spacer(Modifier.height(Sp.xl))
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
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Tune, null) }, title = { Text(if (initial.id == 0L) "Thêm nút" else "Sửa nút") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Sp.s)) {
            OutlinedTextField(label, { label = it }, label = { Text("Tên nút (vd: Power, Vol_up)") }, singleLine = true)
            Box { OutlinedButton(onClick = { protoMenu = true }) { Text("Giao thức: $proto"); Icon(Icons.Default.ArrowDropDown, null) }
                DropdownMenu(protoMenu, { protoMenu = false }) { Encoders.PROTOCOLS.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { proto = p; protoMenu = false }) } } }
            if (isData) {
                if (proto == "RAW") OutlinedTextField(freq, { freq = it }, label = { Text("Tần số (Hz)") }, singleLine = true)
                OutlinedTextField(data, { data = it }, label = { Text(if (proto == "RAW") "Xung µs (mark space …)" else "Pronto Hex (0000 006D …)") }, minLines = 3, maxLines = 6)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                    OutlinedTextField(addr, { addr = it }, Modifier.weight(1f), label = { Text("Địa chỉ (hex)") }, singleLine = true)
                    OutlinedTextField(cmd, { cmd = it }, Modifier.weight(1f), label = { Text("Lệnh (hex)") }, singleLine = true)
                }
                Text("Định dạng Flipper \"07 00 00 00\" ⇒ nhập 7", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (err != null) Text("⚠ $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(enabled = err == null, onClick = { onSave(current()) }) { Text("Lưu") } },
        dismissButton = { Row { TextButton(enabled = err == null, onClick = { onTest(current()) }) { Icon(Icons.Default.Sensors, null); Text(" Thử") }; TextButton(onClick = onDismiss) { Text("Huỷ") } } })
}

@Composable
fun RemoteSettings(vm: MainViewModel, r: RemoteEntity, onDismiss: () -> Unit) {
    val rooms by vm.rooms.collectAsState()
    var name by remember { mutableStateOf(r.name) }
    var type by remember { mutableStateOf(typeOf(r.deviceType)) }
    var room by remember { mutableStateOf(r.roomId) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Thông tin remote") }, text = { Column {
        OutlinedTextField(name, { name = it }, label = { Text("Tên") }, singleLine = true)
        SectionTitle("Loại thiết bị", Modifier.padding(top = Sp.s))
        FlowRowChips(DeviceType.entries.map { it.label }, DeviceType.entries.indexOf(type)) { type = DeviceType.entries[it] }
        SectionTitle("Phòng", Modifier.padding(top = Sp.s))
        FlowRowChips(listOf("Không") + rooms.map { it.name }, if (room == null) 0 else rooms.indexOfFirst { it.id == room } + 1) { room = if (it == 0) null else rooms[it - 1].id }
    } }, confirmButton = { Button(onClick = { vm.updateRemote(r.copy(name = name, deviceType = type.name, roomId = room)); onDismiss() }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.forEachIndexed { i, o -> FilterChip(i == selected, { onSelect(i) }, { Text(o) }) } }
}
