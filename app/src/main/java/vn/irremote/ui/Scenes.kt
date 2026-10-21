package vn.irremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import vn.irremote.App
import vn.irremote.data.ButtonEntity
import vn.irremote.data.MacroEntity
import vn.irremote.data.MacroStep

private val sceneGradients = listOf(listOf(Color(0xFF6C7BFF), Color(0xFF3DD6F5)), listOf(Color(0xFFFF5A5F), Color(0xFFFF9F43)),
    listOf(Color(0xFF34D399), Color(0xFF0091B3)), listOf(Color(0xFFB06AB3), Color(0xFF4568DC)))
private fun stepsOf(m: MacroEntity) = runCatching { App.json.decodeFromString<List<MacroStep>>(m.steps) }.getOrDefault(emptyList())

@Composable
fun ScenesContent(macros: List<MacroEntity>, onRun: (MacroEntity) -> Unit, onEdit: (MacroEntity) -> Unit, onNew: () -> Unit) {
    if (macros.isEmpty()) { EmptyState(Icons.Default.AutoAwesome, "Chưa có cảnh nào", "Gộp nhiều lệnh thành một chạm. Ví dụ \"Xem phim\": bật TV → bật máy chiếu → tắt tiếng loa.", "Tạo cảnh đầu tiên", onNew); return }
    LazyColumn(contentPadding = PaddingValues(Sp.l, Sp.s, Sp.l, 96.dp), verticalArrangement = Arrangement.spacedBy(Sp.m)) {
        itemsIndexed(macros, key = { _, m -> m.id }) { i, m ->
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brush.linearGradient(sceneGradients[i % sceneGradients.size])).clickable { onEdit(m) }) {
                Row(Modifier.padding(Sp.l), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("${stepsOf(m).size} bước · chạm để sửa", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    }
                    RemoteKey({ onRun(m) }, size = 56.dp, container = Color.White.copy(alpha = 0.25f), content = Color.White, glowColor = Color.White) { KeyIcon(Icons.Default.PlayArrow, "Chạy", 30.dp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditor(vm: MainViewModel, id: Long, nav: NavController) {
    val macros by vm.macros.collectAsState()
    val remotes by vm.remotes.collectAsState()
    val existing = macros.firstOrNull { it.id == id }
    var all by remember { mutableStateOf<List<ButtonEntity>>(emptyList()) }
    LaunchedEffect(Unit) { all = vm.allButtons() }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "Cảnh mới") }
    val steps = remember(existing) { mutableStateListOf<MacroStep>().apply { existing?.let { addAll(stepsOf(it)) } } }
    var picking by remember { mutableStateOf(false) }
    fun labelOf(bid: Long) = all.firstOrNull { it.id == bid }?.let { b -> (remotes.firstOrNull { it.id == b.remoteId }?.name ?: "?") + " › " + b.label.replace('_', ' ') } ?: "(nút đã xoá)"
    Scaffold(topBar = { TopAppBar(title = { Text(if (existing == null) "Cảnh mới" else "Sửa cảnh") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
        actions = {
            if (existing != null) IconButton(onClick = { vm.deleteMacro(existing); nav.popBackStack() }) { Icon(Icons.Default.DeleteOutline, "Xoá") }
            Button(onClick = { vm.saveMacro(MacroEntity(id = existing?.id ?: 0, name = name, steps = App.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MacroStep.serializer()), steps.toList()))); nav.popBackStack() },
                Modifier.padding(end = Sp.s)) { Text("Lưu") }
        }) }) { pad ->
        Column(Modifier.padding(pad).padding(Sp.l), verticalArrangement = Arrangement.spacedBy(Sp.m)) {
            OutlinedTextField(name, { name = it }, label = { Text("Tên cảnh") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            SectionTitle("Các bước")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Sp.s)) {
                if (steps.isEmpty()) item { Text("Chưa có bước nào. Thêm nút từ các remote của bạn.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                itemsIndexed(steps) { i, s ->
                    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.medium) {
                        ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Box(Modifier.size(32.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text("${i + 1}") } },
                            headlineContent = { Text(labelOf(s.buttonId)) }, supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)); Text(" chờ ${s.delayMs} ms  ")
                                    TextButton(onClick = { steps[i] = s.copy(delayMs = (s.delayMs - 200).coerceAtLeast(0)) }) { Text("−") }
                                    TextButton(onClick = { steps[i] = s.copy(delayMs = s.delayMs + 200) }) { Text("+") } }
                            }, trailingContent = { IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Default.Close, "Bỏ") } })
                    }
                }
            }
            Button(onClick = { picking = true }, Modifier.fillMaxWidth().height(52.dp), enabled = all.isNotEmpty()) { Icon(Icons.Default.Add, null); Text(if (all.isEmpty()) " Chưa có nút nào để thêm" else " Thêm bước") }
        }
    }
    if (picking) AlertDialog(onDismissRequest = { picking = false }, confirmButton = {}, title = { Text("Chọn nút") }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) { items(all) { b -> ListItem(headlineContent = { Text(labelOf(b.id)) }, modifier = Modifier.clickable { steps.add(MacroStep(b.id)); picking = false }) } }
    })
}
