package vn.irremote.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import vn.irremote.App
import vn.irremote.ac.AcState
import vn.irremote.data.*
import vn.irremote.hw.IrTransmitter

@Composable
fun Messages(vm: MainViewModel, host: SnackbarHostState) {
    LaunchedEffect(Unit) { vm.messages.collect { host.showSnackbar(it) } }
}

enum class AddKind { WIZARD, BLANK, IMPORT }

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController, prefs: UiPrefs, onMic: () -> Unit, listening: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val host = remember { SnackbarHostState() }; Messages(vm, host)
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val remotes by vm.remotes.collectAsState(); val rooms by vm.rooms.collectAsState(); val macros by vm.macros.collectAsState()
    val tick by vm.tick.collectAsState()
    val importIr = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        vm.importIr(uri.lastPathSegment?.substringAfterLast('/') ?: "Remote", text)
    }
    HomeContent(tab, { tab = it }, vm.irAvailable, tick, listening, onMic, host,
        onAdd = { k -> when (k) {
            AddKind.WIZARD -> nav.navigate("wizard")
            AddKind.BLANK -> scope.launch { val id = vm.saveRemote("Remote mới", DeviceType.OTHER, "", emptyList(), "custom"); nav.navigate("remote/$id") }
            AddKind.IMPORT -> importIr.launch(arrayOf("*/*"))
        } },
        onNewScene = { nav.navigate("macro/0") }) {
        when (it) {
            0 -> DevicesContent(remotes, rooms, { r -> nav.navigate("remote/${r.id}") }, vm::quickPower, acOf = vm::acState) { nav.navigate("wizard") }
            1 -> ScenesContent(macros, onRun = vm::runMacro, onEdit = { m -> nav.navigate("macro/${m.id}") }, onNew = { nav.navigate("macro/0") })
            else -> SettingsScreen(vm, prefs)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    tab: Int, onTab: (Int) -> Unit, irAvailable: Boolean, tick: Long, listening: Boolean, onMic: () -> Unit,
    host: SnackbarHostState, onAdd: (AddKind) -> Unit, onNewScene: () -> Unit, body: @Composable (Int) -> Unit,
) {
    var addMenu by remember { mutableStateOf(false) }
    val titles = listOf("Nhà của tôi", "Cảnh", "Cài đặt")
    Scaffold(
        topBar = { TopAppBar(title = { Text(titles[tab], style = MaterialTheme.typography.headlineMedium) }, actions = {
            IrLed(tick)
            FilledTonalIconButton(onClick = onMic, Modifier.padding(end = Sp.s)) { Icon(if (listening) Icons.Default.GraphicEq else Icons.Default.Mic, "Giọng nói") }
        }) },
        snackbarHost = { SnackbarHost(host) },
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            listOf(Triple("Thiết bị", Icons.Filled.SettingsRemote, Icons.Outlined.SettingsRemote),
                Triple("Cảnh", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
                Triple("Cài đặt", Icons.Filled.Settings, Icons.Outlined.Settings)).forEachIndexed { i, (t, on, off) ->
                NavigationBarItem(selected = tab == i, onClick = { onTab(i) }, icon = { Icon(if (tab == i) on else off, t) }, label = { Text(t) })
            }
        } },
        floatingActionButton = {
            when (tab) {
                0 -> Box {
                    ExtendedFloatingActionButton(onClick = { addMenu = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Thêm thiết bị") })
                    DropdownMenu(addMenu, { addMenu = false }) {
                        DropdownMenuItem(leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) }, text = { Text("Dò mã từng bước") }, onClick = { addMenu = false; onAdd(AddKind.WIZARD) })
                        DropdownMenuItem(leadingIcon = { Icon(Icons.Default.Edit, null) }, text = { Text("Tạo remote trống") }, onClick = { addMenu = false; onAdd(AddKind.BLANK) })
                        DropdownMenuItem(leadingIcon = { Icon(Icons.Default.FileOpen, null) }, text = { Text("Nhập file .ir") }, onClick = { addMenu = false; onAdd(AddKind.IMPORT) })
                    }
                }
                1 -> ExtendedFloatingActionButton(onClick = onNewScene, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Cảnh mới") })
                else -> {}
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (!irAvailable) Card(Modifier.fillMaxWidth().padding(horizontal = Sp.l, vertical = Sp.s),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(Sp.m), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SensorsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer); Spacer(Modifier.width(Sp.m))
                    Text(IrTransmitter.NO_IR, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { body(it) }
        }
    }
}

@Composable
fun DevicesContent(remotes: List<RemoteEntity>, rooms: List<RoomEntity>, onOpen: (RemoteEntity) -> Unit, onPower: (RemoteEntity) -> Unit,
                   acOf: (RemoteEntity) -> AcState = { AcState() }, onWizard: () -> Unit) {
    if (remotes.isEmpty()) {
        EmptyState(Icons.Default.SettingsRemote, "Chưa có thiết bị nào", "Hướng điện thoại vào TV, điều hoà hay quạt — trình dò mã sẽ giúp bạn tìm đúng bộ mã chỉ trong vài lần bấm.", "Dò mã thiết bị", onWizard)
        return
    }
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Tất cả", "Yêu thích") + rooms.map { it.name }
    val shown = when (sel) { 0 -> remotes; 1 -> remotes.filter { it.favorite }; else -> remotes.filter { it.roomId == rooms.getOrNull(sel - 2)?.id } }
    Column {
        ScrollableTabRow(sel.coerceAtMost(tabs.lastIndex), edgePadding = Sp.l, containerColor = MaterialTheme.colorScheme.surface, divider = {}) {
            tabs.forEachIndexed { i, t -> Tab(sel == i, { sel = i }, text = { Text(t) }) }
        }
        LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(Sp.l, Sp.l, Sp.l, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(Sp.m), verticalArrangement = Arrangement.spacedBy(Sp.m)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("${shown.size} thiết bị" + (rooms.getOrNull(sel - 2)?.let { " · ${it.name}" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Default.MeetingRoom, "Phòng này đang trống", "Mở một remote → ⋮ → \"Sửa thông tin / phòng\" để xếp thiết bị vào đây.")
            }
            items(shown, key = { it.id }) { r -> DeviceCard(r, rooms.firstOrNull { it.id == r.roomId }?.name, acOf(r), { onOpen(r) }, { onPower(r) }) }
        }
    }
}

@Composable
fun DeviceCard(r: RemoteEntity, room: String?, ac: AcState, onClick: () -> Unit, onPower: () -> Unit) {
    val t = typeOf(r.deviceType); val c = MaterialTheme.colorScheme
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(c.surfaceContainer)) {
        Column(Modifier.padding(Sp.l), verticalArrangement = Arrangement.spacedBy(Sp.s)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(t.icon(), t.tint()); Spacer(Modifier.weight(1f))
                if (r.favorite) Icon(Icons.Default.Star, "Yêu thích", Modifier.size(18.dp), tint = Brand.Warm)
            }
            Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(t.label, room).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (t == DeviceType.AC) (if (ac.power) "${ac.temp}°C · ${ac.mode.label}" else "Đang tắt") else r.brand.ifBlank { "Tuỳ chỉnh" },
                    style = MaterialTheme.typography.labelMedium, color = if (t == DeviceType.AC && ac.power) t.tint() else c.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                RemoteKey(onPower, size = 38.dp, container = Brand.Power.copy(alpha = 0.15f), content = Brand.Power, glowColor = Brand.Power) { KeyIcon(Icons.Default.PowerSettingsNew, "Nguồn", 20.dp) }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, prefs: UiPrefs) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val rooms by vm.rooms.collectAsState()
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val j = vm.backupJson(); ctx.contentResolver.openOutputStream(uri)?.use { it.write(j.toByteArray()) }; vm.messages.tryEmit("Đã sao lưu") }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }?.let { vm.restore(it) }
    }
    SettingsContent(prefs.mode.value, prefs::setMode, prefs.dynamic.value, prefs::setDynamic, vm.irAvailable, vm.irFreq, rooms,
        vm::addRoom, vm::deleteRoom, { backup.launch("irremote-backup.json") }, { restore.launch(arrayOf("application/json", "*/*")) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(mode: ThemeMode, onMode: (ThemeMode) -> Unit, dynamic: Boolean, onDynamic: (Boolean) -> Unit, irAvailable: Boolean, irFreq: String,
                    rooms: List<RoomEntity>, onAddRoom: (String) -> Unit, onDeleteRoom: (RoomEntity) -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    var newRoom by remember { mutableStateOf("") }
    @Composable fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
        SectionTitle(title, Modifier.padding(top = Sp.m))
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.large) { Column(Modifier.padding(Sp.l), content = content) }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Sp.l).padding(bottom = Sp.xxl)) {
        Group("Giao diện") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m -> SegmentedButton(mode == m, { onMode(m) }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(m.label) } }
            }
            Row(Modifier.padding(top = Sp.m), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Màu động (Material You)", style = MaterialTheme.typography.bodyLarge)
                    Text("Lấy màu theo hình nền, Android 12+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(dynamic, onDynamic)
            }
        }
        Group("Phần cứng") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (irAvailable) Icons.Default.Sensors else Icons.Default.SensorsOff, if (irAvailable) Brand.Mint else Brand.Power, 40.dp)
                Spacer(Modifier.width(Sp.m))
                Column { Text(if (irAvailable) "Có cổng phát hồng ngoại" else "Không có cổng phát hồng ngoại", style = MaterialTheme.typography.bodyLarge)
                    Text("Tần số: $irFreq", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Group("Phòng") {
            rooms.forEach { r -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MeetingRoom, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(Sp.m))
                Text(r.name, Modifier.weight(1f)); IconButton(onClick = { onDeleteRoom(r) }) { Icon(Icons.Default.DeleteOutline, "Xoá") } } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newRoom, { newRoom = it }, Modifier.weight(1f), placeholder = { Text("Phòng khách, Phòng ngủ…") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                Spacer(Modifier.width(Sp.s))
                FilledIconButton(onClick = { onAddRoom(newRoom); newRoom = "" }) { Icon(Icons.Default.Add, "Thêm phòng") }
            }
        }
        Group("Sao lưu") {
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                Button(onBackup) { Icon(Icons.Default.Backup, null); Spacer(Modifier.width(Sp.s)); Text("Sao lưu JSON") }
                OutlinedButton(onRestore) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(Sp.s)); Text("Khôi phục") }
            }
            Text("Khôi phục sẽ thay thế toàn bộ dữ liệu hiện tại.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Sp.s))
        }
        Group("Giới thiệu") {
            Text("IR Remote VN 0.2 — miễn phí, không quảng cáo, không cần tài khoản. Dữ liệu chỉ lưu trên máy.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Sp.s))
            Text("Kho mã khởi đầu: Flipper-IRDB (github.com/Lucaslhm/Flipper-IRDB), giấy phép CC0-1.0, chỉ các file thêm sau commit 2319685.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Sp.s))
            Text("Lệnh nói: \"tắt tivi\", \"tăng âm lượng\", \"kênh 12\", \"điều hoà 25 độ\", \"chạy cảnh xem phim\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Suppress("unused") private val keepApp = App::class
