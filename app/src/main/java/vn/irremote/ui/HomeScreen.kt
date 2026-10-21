package vn.irremote.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import vn.irremote.data.DeviceType
import vn.irremote.data.RemoteEntity

@Composable
fun Messages(vm: MainViewModel, host: SnackbarHostState) {
    LaunchedEffect(Unit) { vm.messages.collect { host.showSnackbar(it) } }
}

fun DeviceType.icon() = when (this) {
    DeviceType.TV -> Icons.Default.Tv; DeviceType.STB -> Icons.Default.SettingsInputAntenna; DeviceType.FAN -> Icons.Default.Air
    DeviceType.AC -> Icons.Default.AcUnit; DeviceType.PROJECTOR -> Icons.Default.Videocam; DeviceType.OTHER -> Icons.Default.SettingsRemote
}
fun typeOf(s: String) = runCatching { DeviceType.valueOf(s) }.getOrDefault(DeviceType.OTHER)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController, onMic: () -> Unit, listening: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val host = remember { SnackbarHostState() }
    Messages(vm, host)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val importIr = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        vm.importIr(uri.lastPathSegment?.substringAfterLast('/') ?: "Remote", text)
    }
    var addMenu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("IR Remote VN") }, actions = {
            IconButton(onClick = onMic) { Icon(if (listening) Icons.Default.GraphicEq else Icons.Default.Mic, "Giọng nói") }
        }) },
        snackbarHost = { SnackbarHost(host) },
        bottomBar = { NavigationBar {
            listOf("Thiết bị" to Icons.Default.SettingsRemote, "Cảnh" to Icons.Default.AutoAwesome, "Cài đặt" to Icons.Default.Settings).forEachIndexed { i, (t, ic) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, t) }, label = { Text(t) })
            }
        } },
        floatingActionButton = {
            if (tab == 0) Box {
                ExtendedFloatingActionButton(onClick = { addMenu = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Thêm") })
                DropdownMenu(addMenu, { addMenu = false }) {
                    DropdownMenuItem(text = { Text("Dò mã thiết bị (từng bước)") }, onClick = { addMenu = false; nav.navigate("wizard") })
                    DropdownMenuItem(text = { Text("Tạo remote trống") }, onClick = {
                        addMenu = false; scope.launch { val id = vm.saveRemote("Remote mới", DeviceType.OTHER, "", emptyList(), "custom"); nav.navigate("remote/$id") }
                    })
                    DropdownMenuItem(text = { Text("Nhập file .ir") }, onClick = { addMenu = false; importIr.launch(arrayOf("*/*")) })
                }
            } else if (tab == 1) FloatingActionButton(onClick = { nav.navigate("macro/0") }) { Icon(Icons.Default.Add, "Thêm cảnh") }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (!vm.irAvailable) Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                Text(vn.irremote.hw.IrTransmitter.NO_IR, Modifier.padding(12.dp))
            }
            when (tab) { 0 -> DevicesTab(vm, nav); 1 -> ScenesTab(vm, nav); else -> SettingsTab(vm) }
        }
    }
}

@Composable
fun DevicesTab(vm: MainViewModel, nav: NavController) {
    val remotes by vm.remotes.collectAsState(); val rooms by vm.rooms.collectAsState()
    if (remotes.isEmpty()) { Text("Chưa có thiết bị. Bấm \"Thêm\" → \"Dò mã thiết bị\" để bắt đầu.", Modifier.padding(16.dp)); return }
    val groups = buildList {
        val fav = remotes.filter { it.favorite }; if (fav.isNotEmpty()) add("★ Yêu thích" to fav)
        rooms.forEach { r -> remotes.filter { it.roomId == r.id }.takeIf { it.isNotEmpty() }?.let { add(r.name to it) } }
        remotes.filter { it.roomId == null || rooms.none { r -> r.id == it.roomId } }.takeIf { it.isNotEmpty() }?.let { add("Chưa xếp phòng" to it) }
    }
    LazyColumn(contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 88.dp)) {
        groups.forEach { (title, list) ->
            item { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp)) }
            items(list, key = { title + it.id }) { r -> RemoteRow(r) { nav.navigate("remote/${r.id}") } }
        }
    }
}

@Composable
fun RemoteRow(r: RemoteEntity, onClick: () -> Unit) {
    val t = typeOf(r.deviceType)
    ListItem(modifier = Modifier.clickable(onClick = onClick), leadingContent = { Icon(t.icon(), null) },
        headlineContent = { Text(r.name) }, supportingContent = { Text(listOf(t.label, r.brand).filter { it.isNotBlank() }.joinToString(" · ")) },
        trailingContent = { if (r.favorite) Icon(Icons.Default.Star, null) })
}

@Composable
fun SettingsTab(vm: MainViewModel) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val rooms by vm.rooms.collectAsState()
    var newRoom by remember { mutableStateOf("") }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val j = vm.backupJson(); ctx.contentResolver.openOutputStream(uri)?.use { it.write(j.toByteArray()) }; vm.messages.tryEmit("Đã sao lưu") }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }?.let { vm.restore(it) }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Phần cứng IR", style = MaterialTheme.typography.titleMedium)
        Text(if (vm.irAvailable) "Có cổng phát IR · Tần số: ${vm.irFreq}" else "Không có cổng phát IR")
        HorizontalDivider()
        Text("Phòng", style = MaterialTheme.typography.titleMedium)
        rooms.forEach { r -> Row { Text(r.name, Modifier.weight(1f).padding(top = 12.dp)); IconButton(onClick = { vm.deleteRoom(r) }) { Icon(Icons.Default.Delete, "Xoá") } } }
        Row { OutlinedTextField(newRoom, { newRoom = it }, Modifier.weight(1f), label = { Text("Tên phòng mới") }, singleLine = true)
            IconButton(onClick = { vm.addRoom(newRoom); newRoom = "" }) { Icon(Icons.Default.Add, "Thêm phòng") } }
        HorizontalDivider()
        Text("Sao lưu", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { backup.launch("irremote-backup.json") }) { Text("Sao lưu JSON") }
            OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "*/*")) }) { Text("Khôi phục") }
        }
        Text("Khôi phục sẽ thay thế toàn bộ dữ liệu hiện tại.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Giới thiệu", style = MaterialTheme.typography.titleMedium)
        Text("Ứng dụng miễn phí, không quảng cáo, không cần tài khoản, dữ liệu chỉ lưu trên máy.\n\n" +
            "Kho mã khởi đầu lấy từ Flipper-IRDB (github.com/Lucaslhm/Flipper-IRDB), giấy phép CC0-1.0 — chỉ dùng các file được thêm sau commit 2319685. Cảm ơn cộng đồng Flipper-IRDB.\n\n" +
            "Lệnh giọng nói ví dụ: \"tắt tivi\", \"tăng âm lượng\", \"kênh 12\", \"điều hoà 25 độ\", \"chế độ lạnh\", \"chạy cảnh xem phim\".")
    }
}
