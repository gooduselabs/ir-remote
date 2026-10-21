package vn.irremote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: String, val body: String)

@Composable
fun OnboardingContent(irAvailable: Boolean, onDone: () -> Unit) {
    val pages = listOf(
        Page(Icons.Default.SettingsRemote, "Một chiếc điện thoại,\nmọi chiếc remote", "Điều khiển TV, điều hoà, quạt, đầu thu và máy chiếu bằng cổng hồng ngoại có sẵn trên máy."),
        Page(Icons.Default.AutoFixHigh, "Dò mã trong vài chạm", "Chọn loại thiết bị và hãng, bấm thử, trả lời \"Có\" hoặc \"Không\" — app tìm đúng bộ mã cho bạn."),
        Page(Icons.Default.AutoAwesome, "Cảnh, widget & giọng nói", "Gộp nhiều lệnh thành một cảnh, đặt nút ra màn hình chính, hoặc nói \"điều hoà 25 độ\"."),
    )
    val st = rememberPagerState { pages.size }; val scope = rememberCoroutineScope(); val c = MaterialTheme.colorScheme
    Surface(color = c.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(Sp.xl)) {
            Row { Spacer(Modifier.weight(1f)); TextButton(onDone) { Text("Bỏ qua") } }
            HorizontalPager(st, Modifier.weight(1f)) { i ->
                val p = pages[i]
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(200.dp).clip(CircleShape).background(Brush.radialGradient(listOf(c.primary.copy(alpha = 0.45f), c.secondary.copy(alpha = 0.12f), c.background))), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(112.dp).clip(RoundedCornerShape(36.dp)).background(Brush.linearGradient(listOf(c.primary, c.secondary))), contentAlignment = Alignment.Center) {
                            Icon(p.icon, null, Modifier.size(60.dp), tint = c.onPrimary)
                        }
                    }
                    Spacer(Modifier.height(Sp.xxl))
                    Text(p.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Sp.m))
                    Text(p.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = c.onSurfaceVariant)
                    if (i == pages.lastIndex) {
                        Spacer(Modifier.height(Sp.xl))
                        AssistChip({}, { Text(if (irAvailable) "Máy bạn có cổng phát hồng ngoại ✓" else "Máy bạn không có cổng phát hồng ngoại") },
                            leadingIcon = { Icon(if (irAvailable) Icons.Default.Sensors else Icons.Default.SensorsOff, null) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    val w by animateDpAsState(if (st.currentPage == i) 28.dp else 8.dp, label = "w")
                    val col by animateColorAsState(if (st.currentPage == i) c.primary else c.outline, label = "c")
                    Box(Modifier.padding(4.dp).height(8.dp).width(w).clip(CircleShape).background(col))
                }
            }
            Spacer(Modifier.height(Sp.xl))
            Button({ if (st.currentPage < pages.lastIndex) scope.launch { st.animateScrollToPage(st.currentPage + 1) } else onDone() }, Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (st.currentPage < pages.lastIndex) "Tiếp tục" else "Bắt đầu")
            }
        }
    }
}
