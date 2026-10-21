package vn.irremote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import vn.irremote.data.CatalogEntry
import vn.irremote.data.DeviceType
import vn.irremote.voice.ButtonKeys

/** Trình dò mã từng bước: chọn loại → hãng → thử từng bộ mã, hỏi "Thiết bị có phản ứng không?". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(vm: MainViewModel, nav: NavController) {
    var type by remember { mutableStateOf<DeviceType?>(null) }
    var brand by remember { mutableStateOf<String?>(null) }
    var brandChosen by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var testIdx by remember { mutableIntStateOf(0) }
    val host = remember { SnackbarHostState() }; Messages(vm, host)
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text("Dò mã thiết bị") }, navigationIcon = {
        IconButton(onClick = { when { brandChosen -> { brandChosen = false; index = 0 }; type != null -> type = null; else -> nav.popBackStack() } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } }) },
        snackbarHost = { SnackbarHost(host) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val t = type
            if (t == null) {
                Text("Bước 1: Chọn loại thiết bị", style = MaterialTheme.typography.titleMedium)
                DeviceType.entries.filter { it != DeviceType.OTHER }.forEach { d ->
                    ElevatedButton(onClick = { type = d }, Modifier.fillMaxWidth()) { Icon(d.icon(), null); Text("  " + d.label + " (${vm.catalog.candidates(d, null).size} bộ mã)") }
                }
                return@Column
            }
            if (!brandChosen) {
                Text("Bước 2: Chọn hãng ${t.label}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { brand = null; brandChosen = true; index = 0 }, Modifier.fillMaxWidth()) { Text("Không rõ hãng — thử tất cả") }
                vm.catalog.brands(t).forEach { b -> ElevatedButton(onClick = { brand = b; brandChosen = true; index = 0; testIdx = 0 }, Modifier.fillMaxWidth()) { Text(b) } }
                if (vm.catalog.brands(t).isEmpty()) Text("Kho mã kèm app chưa có ${t.label}. Hãy nhập file .ir hoặc tạo remote trống.")
                return@Column
            }
            val cands: List<CatalogEntry> = remember(t, brand) { vm.catalog.candidates(t, brand) }
            if (index >= cands.size) {
                Text("Đã thử hết ${cands.size} bộ mã mà chưa khớp.", style = MaterialTheme.typography.titleMedium)
                Text("Gợi ý: chọn \"Không rõ hãng\" để thử thêm, nhập file .ir của thiết bị, hoặc tạo remote trống và tự nhập mã.")
                Button(onClick = { brandChosen = false; index = 0 }) { Text("Chọn lại hãng") }
                return@Column
            }
            val e = cands[index]
            val codes = remember(e) { runCatching { vm.catalog.load(e) }.getOrDefault(emptyList()) }
            val names = codes.map { it.name }
            val primary = ButtonKeys.testCandidate(names)
            val tests = (listOfNotNull(primary) + names.filter { it != primary }).take(5)
            val test = codes.firstOrNull { it.name == tests.getOrNull(testIdx % tests.size.coerceAtLeast(1)) }
            Text("Bước 3: Thử bộ mã ${index + 1}/${cands.size}", style = MaterialTheme.typography.titleMedium)
            Text("${e.brand} · ${e.model} · ${codes.size} nút", fontSize = 13.sp)
            Text("Hướng điện thoại về phía thiết bị rồi bấm nút gửi thử.")
            Button(onClick = { test?.let { vm.sendCode(it) } }, Modifier.fillMaxWidth().height(72.dp), enabled = test != null) {
                Icon(Icons.Default.PowerSettingsNew, null); Text("  Gửi \"${test?.name ?: "?"}\"", fontSize = 18.sp)
            }
            if (tests.size > 1) TextButton(onClick = { testIdx++ }) { Text("Thử nút khác") }
            Text("Thiết bị có phản ứng không?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    scope.launch { val id = vm.saveRemote("${e.brand} ${t.label}", t, e.brand, codes, "Flipper-IRDB:" + e.path)
                        nav.navigate("remote/$id") { popUpTo("home") } }
                }, Modifier.weight(1f)) { Icon(Icons.Default.Check, null); Text(" Có") }
                OutlinedButton(onClick = { index++; testIdx = 0 }, Modifier.weight(1f)) { Icon(Icons.Default.Close, null); Text(" Không, thử tiếp") }
            }
        }
    }
}
