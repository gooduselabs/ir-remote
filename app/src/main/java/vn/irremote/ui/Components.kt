package vn.irremote.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.irremote.data.DeviceType

fun DeviceType.icon(): ImageVector = when (this) {
    DeviceType.TV -> Icons.Default.Tv; DeviceType.STB -> Icons.Default.Router; DeviceType.FAN -> Icons.Default.Air
    DeviceType.AC -> Icons.Default.AcUnit; DeviceType.PROJECTOR -> Icons.Default.Videocam; DeviceType.OTHER -> Icons.Default.SettingsRemote
}
fun DeviceType.tint(): Color = when (this) {
    DeviceType.TV -> Brand.Cyan; DeviceType.STB -> Brand.Indigo; DeviceType.FAN -> Brand.Mint
    DeviceType.AC -> Brand.Cool; DeviceType.PROJECTOR -> Brand.Warm; DeviceType.OTHER -> Color(0xFFB39DDB)
}
fun typeOf(s: String) = runCatching { DeviceType.valueOf(s) }.getOrDefault(DeviceType.OTHER)

/**
 * Phím remote: thu nhỏ khi nhấn, rung nhẹ, và loé sáng (glow) khi phát IR.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteKey(
    onClick: () -> Unit, modifier: Modifier = Modifier, shape: Shape = CircleShape, size: Dp = 64.dp, width: Dp = size,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh, content: Color = MaterialTheme.colorScheme.onSurface,
    glowColor: Color = MaterialTheme.colorScheme.primary, onLongClick: (() -> Unit)? = null, label: String? = null,
    inner: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "s")
    val glow = remember { Animatable(0f) }
    var fire by remember { mutableIntStateOf(0) }
    LaunchedEffect(fire) { if (fire > 0) { glow.snapTo(1f); glow.animateTo(0f, tween(450)) } }
    Box(modifier.size(width, size).scale(scale)
        .drawBehind { if (glow.value > 0f) drawCircle(Brush.radialGradient(listOf(glowColor.copy(alpha = 0.55f * glow.value), Color.Transparent)), radius = this.size.maxDimension * 0.85f) }
        .clip(shape)
        .background(Brush.verticalGradient(listOf(container, container.copy(alpha = container.alpha * 0.82f))))
        .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
        .combinedClickable(src, ripple(color = glowColor), onLongClick = onLongClick) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress); fire++; onClick()
        }, contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides content) { inner() }
    }
    if (label != null) Unit
}

@Composable
fun KeyIcon(icon: ImageVector, desc: String, size: Dp = 26.dp) = Icon(icon, desc, Modifier.size(size))

@Composable
fun KeyText(t: String, size: Int = 15) = Text(t, fontSize = size.sp, textAlign = TextAlign.Center, maxLines = 2, style = MaterialTheme.typography.labelLarge)

@Composable
fun PowerKey(onClick: () -> Unit, size: Dp = 64.dp) = RemoteKey(onClick, size = size, container = Brand.Power, content = Color.White, glowColor = Brand.Power) {
    KeyIcon(Icons.Default.PowerSettingsNew, "Nguồn", 30.dp)
}

/** D-pad tròn: 4 hướng + OK ở giữa. Phím nào null thì ẩn. */
@Composable
fun DPad(up: (() -> Unit)?, down: (() -> Unit)?, left: (() -> Unit)?, right: (() -> Unit)?, ok: (() -> Unit)?, size: Dp = 236.dp) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(size).clip(CircleShape)
        .background(Brush.radialGradient(listOf(c.surfaceContainerHighest, c.surfaceContainerHigh)))
        .border(1.dp, c.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
        val k = size / 3.4f
        @Composable fun Arrow(f: (() -> Unit)?, icon: ImageVector, d: String, a: Alignment) {
            if (f != null) Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = a) {
                RemoteKey(f, size = k, container = Color.Transparent) { KeyIcon(icon, d, 32.dp) }
            }
        }
        Arrow(up, Icons.Default.KeyboardArrowUp, "Lên", Alignment.TopCenter)
        Arrow(down, Icons.Default.KeyboardArrowDown, "Xuống", Alignment.BottomCenter)
        Arrow(left, Icons.Default.KeyboardArrowLeft, "Trái", Alignment.CenterStart)
        Arrow(right, Icons.Default.KeyboardArrowRight, "Phải", Alignment.CenterEnd)
        if (ok != null) RemoteKey(ok, size = size * 0.36f, container = c.primary, content = c.onPrimary) { Text("OK", style = MaterialTheme.typography.titleMedium) }
    }
}

/** Phím bập bênh dọc (âm lượng / kênh). */
@Composable
fun Rocker(label: String, up: (() -> Unit)?, down: (() -> Unit)?, upIcon: ImageVector = Icons.Default.Add, downIcon: ImageVector = Icons.Default.Remove) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.width(72.dp).clip(RoundedCornerShape(36.dp)).background(c.surfaceContainerHigh).border(1.dp, c.outlineVariant, RoundedCornerShape(36.dp)),
        horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteKey(up ?: {}, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp), width = 72.dp, size = 68.dp, container = Color.Transparent,
            content = if (up == null) c.outline else c.onSurface) { KeyIcon(upIcon, "$label tăng") }
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.onSurfaceVariant)
        RemoteKey(down ?: {}, shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp), width = 72.dp, size = 68.dp, container = Color.Transparent,
            content = if (down == null) c.outline else c.onSurface) { KeyIcon(downIcon, "$label giảm") }
    }
}

/** Trạng thái trống đẹp: icon trong vòng tròn gradient + tiêu đề + mô tả + nút hành động. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(Sp.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Sp.m)) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.primary.copy(alpha = 0.35f), c.secondary.copy(alpha = 0.25f)))),
            contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(56.dp), tint = c.primary) }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) Button(onAction, contentPadding = PaddingValues(horizontal = Sp.xl, vertical = Sp.m)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(Sp.s)); Text(action) }
    }
}

/** Đèn IR nhỏ trên thanh tiêu đề, loé sáng mỗi lần phát. */
@Composable
fun IrLed(tick: Long) {
    val a = remember { Animatable(0.25f) }
    LaunchedEffect(tick) { if (tick > 0) { a.snapTo(1f); a.animateTo(0.25f, tween(600)) } }
    val c = MaterialTheme.colorScheme.primary
    Box(Modifier.padding(horizontal = Sp.m).size(12.dp).drawBehind {
        drawCircle(c.copy(alpha = a.value * 0.5f), radius = size.minDimension * 1.3f); drawCircle(c.copy(alpha = a.value))
    })
}

@Composable
fun SectionTitle(t: String, modifier: Modifier = Modifier) =
    Text(t.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(vertical = Sp.s))

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 48.dp) =
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
