package vn.irremote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import vn.irremote.App
import vn.irremote.data.ButtonEntity
import vn.irremote.data.MacroEntity
import vn.irremote.data.MacroStep

@Composable
fun ScenesTab(vm: MainViewModel, nav: NavController) {
    val macros by vm.macros.collectAsState()
    if (macros.isEmpty()) Text("Chưa có cảnh. Ví dụ \"Xem phim\": bật TV → bật loa → tắt đèn. Bấm + để tạo.", Modifier.padding(16.dp))
    LazyColumn(contentPadding = PaddingValues(8.dp)) {
        items(macros, key = { it.id }) { m ->
            ListItem(headlineContent = { Text(m.name) },
                supportingContent = { Text("${runCatching { App.json.decodeFromString<List<MacroStep>>(m.steps).size }.getOrDefault(0)} bước") },
                trailingContent = { Row {
                    IconButton(onClick = { nav.navigate("macro/${m.id}") }) { Icon(Icons.Default.Edit, "Sửa") }
                    FilledIconButton(onClick = { vm.runMacro(m) }) { Icon(Icons.Default.PlayArrow, "Chạy") }
                } })
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
    val steps = remember(existing) { mutableStateListOf<MacroStep>().apply { existing?.let { addAll(App.json.decodeFromString<List<MacroStep>>(it.steps)) } } }
    var picking by remember { mutableStateOf(false) }
    fun labelOf(bid: Long) = all.firstOrNull { it.id == bid }?.let { b -> (remotes.firstOrNull { it.id == b.remoteId }?.name ?: "?") + " › " + b.label } ?: "(nút đã xoá)"
    Scaffold(topBar = { TopAppBar(title = { Text("Sửa cảnh") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
        actions = {
            if (existing != null) IconButton(onClick = { vm.deleteMacro(existing); nav.popBackStack() }) { Icon(Icons.Default.Delete, "Xoá") }
            TextButton(onClick = { vm.saveMacro(MacroEntity(id = existing?.id ?: 0, name = name, steps = App.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MacroStep.serializer()), steps.toList()))); nav.popBackStack() }) { Text("Lưu") }
        }) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Tên cảnh") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(steps) { i, s ->
                    ListItem(headlineContent = { Text("${i + 1}. ${labelOf(s.buttonId)}") }, supportingContent = {
                        Row { Text("Chờ ${s.delayMs} ms  ")
                            TextButton(onClick = { steps[i] = s.copy(delayMs = (s.delayMs - 200).coerceAtLeast(0)) }) { Text("−") }
                            TextButton(onClick = { steps[i] = s.copy(delayMs = s.delayMs + 200) }) { Text("+") } }
                    }, trailingContent = { IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Default.Close, "Bỏ") } })
                }
            }
            Button(onClick = { picking = true }, Modifier.fillMaxWidth(), enabled = all.isNotEmpty()) { Text(if (all.isEmpty()) "Chưa có nút nào để thêm" else "Thêm bước") }
        }
    }
    if (picking) AlertDialog(onDismissRequest = { picking = false }, confirmButton = {}, title = { Text("Chọn nút") }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) { items(all) { b -> TextButton(onClick = { steps.add(MacroStep(b.id)); picking = false }) { Text(labelOf(b.id)) } } }
    })
}
