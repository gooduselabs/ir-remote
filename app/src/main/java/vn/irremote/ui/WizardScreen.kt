package vn.irremote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import vn.irremote.data.DeviceType
import vn.irremote.voice.ButtonKeys

/** Trình dò mã từng bước: chọn loại → hãng → thử từng bộ mã, hỏi "Thiết bị có phản ứng không?". */
@Composable
fun WizardScreen(vm: MainViewModel, nav: NavController) {
    var type by remember { mutableStateOf<DeviceType?>(null) }
    var brand by remember { mutableStateOf<String?>(null) }
    var brandChosen by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var testIdx by remember { mutableIntStateOf(0) }
    val host = remember { SnackbarHostState() }; Messages(vm, host)
    val scope = rememberCoroutineScope()
    val step = when { type == null -> 0; !brandChosen -> 1; else -> 2 }
    WizardFrame(step, host, onBack = { when (step) { 2 -> { brandChosen = false; index = 0 }; 1 -> type = null; else -> nav.popBackStack() } }) {
        when (it) {
            0 -> WizardTypeStep(DeviceType.entries.filter { t -> t != DeviceType.OTHER }.associateWith { t -> vm.catalog.candidates(t, null).size }) { t -> type = t }
            1 -> WizardBrandStep(type!!, vm.catalog.brands(type!!)) { b -> brand = b; brandChosen = true; index = 0; testIdx = 0 }
            else -> {
                val t = type!!
                val cands = remember(t, brand) { vm.catalog.candidates(t, brand) }
                if (index >= cands.size) {
                    EmptyState(Icons.Default.SearchOff, "Chưa tìm thấy bộ mã khớp", "Đã thử ${cands.size} bộ mã. Hãy thử \"Không rõ hãng\", nhập file .ir của thiết bị, hoặc tạo remote trống và tự nhập mã.", "Chọn lại hãng") { brandChosen = false; index = 0 }
                } else {
                    val e = cands[index]
                    val codes = remember(e) { runCatching { vm.catalog.load(e) }.getOrDefault(emptyList()) }
                    val names = codes.map { c -> c.name }
                    val primary = ButtonKeys.testCandidate(names)
                    val tests = (listOfNotNull(primary) + names.filter { n -> n != primary }).take(5)
                    val test = codes.firstOrNull { c -> c.name == tests.getOrNull(testIdx % tests.size.coerceAtLeast(1)) }
                    WizardTestStep(t, e.brand, e.model, codes.size, index, cands.size, test?.name, tests.size > 1,
                        onSend = { test?.let(vm::sendCode) }, onOther = { testIdx++ }, onNo = { index++; testIdx = 0 },
                        onYes = { scope.launch { val id = vm.saveRemote("${e.brand} ${t.label}", t, e.brand, codes, "Flipper-IRDB:" + e.path); nav.navigate("remote/$id") { popUpTo("home") } } })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardFrame(step: Int, host: SnackbarHostState, onBack: () -> Unit, content: @Composable (Int) -> Unit) {
    val prog by animateFloatAsState((step + 1) / 3f, label = "p")
    Scaffold(topBar = { Column {
        TopAppBar(title = { Column { Text("Dò mã thiết bị", style = MaterialTheme.typography.titleLarge)
            Text("Bước ${step + 1}/3 · " + listOf("Loại thiết bị", "Hãng sản xuất", "Thử mã")[step], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } })
        LinearProgressIndicator({ prog }, Modifier.fillMaxWidth().padding(horizontal = Sp.l), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
    } }, snackbarHost = { SnackbarHost(host) }) { pad ->
        AnimatedContent(step, Modifier.padding(pad), transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
        }, label = "step") { content(it) }
    }
}

@Composable
fun WizardTypeStep(counts: Map<DeviceType, Int>, onPick: (DeviceType) -> Unit) {
    Column(Modifier.padding(Sp.l)) {
        Text("Bạn muốn điều khiển gì?", style = MaterialTheme.typography.headlineSmall)
        Text("Chọn loại thiết bị để thu hẹp bộ mã cần thử.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Sp.l))
        LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(Sp.m), verticalArrangement = Arrangement.spacedBy(Sp.m)) {
            items(counts.entries.toList()) { (t, n) ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { onPick(t) }, shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(Sp.l), verticalArrangement = Arrangement.spacedBy(Sp.s)) {
                        IconBadge(t.icon(), t.tint(), 52.dp)
                        Text(t.label, style = MaterialTheme.typography.titleMedium)
                        Text("$n bộ mã", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun WizardBrandStep(type: DeviceType, brands: List<String>, onPick: (String?) -> Unit) {
    var q by remember { mutableStateOf("") }
    Column(Modifier.padding(Sp.l).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sp.s)) {
        Text("Hãng ${type.label} của bạn?", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Tìm hãng…") }, singleLine = true, shape = MaterialTheme.shapes.medium)
        Card(Modifier.fillMaxWidth().clickable { onPick(null) }, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.medium) {
            ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent), leadingContent = { Icon(Icons.Default.HelpOutline, null) },
                headlineContent = { Text("Không rõ hãng") }, supportingContent = { Text("Thử lần lượt tất cả bộ mã ${type.label}") })
        }
        if (brands.isEmpty()) Text("Kho mã kèm app chưa có ${type.label}. Hãy nhập file .ir hoặc tạo remote trống.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        brands.filter { it.contains(q, true) }.forEach { b ->
            Card(Modifier.fillMaxWidth().clickable { onPick(b) }, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.medium) {
                ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Box(Modifier.size(36.dp).clip(CircleShape).background(type.tint().copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Text(b.take(1).uppercase(), color = type.tint(), style = MaterialTheme.typography.titleMedium) } },
                    headlineContent = { Text(b) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) })
            }
        }
    }
}

@Composable
fun WizardTestStep(type: DeviceType, brand: String, model: String, buttons: Int, index: Int, total: Int, testName: String?, canOther: Boolean,
                   onSend: () -> Unit, onOther: () -> Unit, onYes: () -> Unit, onNo: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val k by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "k")
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sp.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Sp.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bộ mã ${index + 1}/$total", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.width(Sp.m))
            LinearProgressIndicator({ (index + 1f) / total }, Modifier.weight(1f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, color = type.tint())
        }
        Text("$brand · $model · $buttons nút", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        Box(Modifier.size(220.dp).drawBehind {
            for (i in 0..2) { val p = (k + i / 3f) % 1f; drawCircle(type.tint().copy(alpha = (1 - p) * 0.35f), radius = size.minDimension / 2 * (0.5f + p * 0.5f)) }
        }, contentAlignment = Alignment.Center) {
            RemoteKey(onSend, size = 120.dp, container = Brand.Power, content = Color.White, glowColor = Brand.Power) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { KeyIcon(Icons.Default.PowerSettingsNew, "Gửi thử", 44.dp); Text("GỬI THỬ", style = MaterialTheme.typography.labelMedium) }
            }
        }
        Text("Hướng điện thoại vào ${type.label.lowercase()} rồi bấm nút đỏ.\nNút sẽ gửi: \"${testName?.replace('_', ' ') ?: "?"}\"", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = c.onSurfaceVariant)
        if (canOther) TextButton(onOther) { Icon(Icons.Default.SwapHoriz, null); Text(" Thử nút khác") }
        Text("Thiết bị có phản ứng không?", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
            OutlinedButton(onNo, Modifier.weight(1f).height(56.dp)) { Icon(Icons.Default.Close, null); Text(" Không, thử tiếp") }
            Button(onYes, Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(Brand.Mint, Color(0xFF00281A))) { Icon(Icons.Default.Check, null); Text(" Có, dùng mã này") }
        }
    }
}
