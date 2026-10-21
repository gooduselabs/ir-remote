package vn.irremote.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bảng màu thương hiệu: xanh điện (IR) + chàm đậm, nút nguồn đỏ cam. */
object Brand {
    val Cyan = Color(0xFF3DD6F5); val CyanDeep = Color(0xFF0091B3)
    val Indigo = Color(0xFF6C7BFF); val Night = Color(0xFF0B0F1A); val NightCard = Color(0xFF141A29); val NightHigh = Color(0xFF1C2438)
    val Power = Color(0xFFFF5A5F); val Warm = Color(0xFFFF9F43); val Cool = Color(0xFF4FC3F7); val Mint = Color(0xFF34D399)
}

private val DarkColors = darkColorScheme(
    primary = Brand.Cyan, onPrimary = Color(0xFF00222B), primaryContainer = Color(0xFF00485A), onPrimaryContainer = Color(0xFFB8F0FF),
    secondary = Brand.Indigo, onSecondary = Color.White, secondaryContainer = Color(0xFF2A3170), onSecondaryContainer = Color(0xFFDDE0FF),
    tertiary = Brand.Warm, error = Brand.Power, errorContainer = Color(0xFF5C1A1D), onErrorContainer = Color(0xFFFFDAD9),
    background = Brand.Night, onBackground = Color(0xFFE6EAF2), surface = Brand.Night, onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Brand.NightHigh, onSurfaceVariant = Color(0xFFA9B2C6), outline = Color(0xFF3A4560), outlineVariant = Color(0xFF263049),
    surfaceContainerLowest = Color(0xFF080B14), surfaceContainerLow = Color(0xFF111726), surfaceContainer = Brand.NightCard,
    surfaceContainerHigh = Brand.NightHigh, surfaceContainerHighest = Color(0xFF242E46),
)
private val LightColors = lightColorScheme(
    primary = Brand.CyanDeep, onPrimary = Color.White, primaryContainer = Color(0xFFC9F1FF), onPrimaryContainer = Color(0xFF002833),
    secondary = Color(0xFF4A57D6), secondaryContainer = Color(0xFFE0E3FF), onSecondaryContainer = Color(0xFF141C6B),
    tertiary = Color(0xFFE07B12), error = Color(0xFFE5383B), errorContainer = Color(0xFFFFDAD9),
    background = Color(0xFFF5F7FB), surface = Color(0xFFF5F7FB), onSurface = Color(0xFF151A26), onSurfaceVariant = Color(0xFF5A6478),
    surfaceVariant = Color(0xFFE6EAF2), outline = Color(0xFFC3CAD8), outlineVariant = Color(0xFFDDE2EC),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFCFE), surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF1F7), surfaceContainerHighest = Color(0xFFE6EAF2),
)

private val F = FontFamily.SansSerif
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.Light, fontSize = 48.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.6.sp),
    bodyLarge = TextStyle(fontFamily = F, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = F, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = F, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
)
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)
object Sp { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp }

enum class ThemeMode(val label: String) { DARK("Tối"), LIGHT("Sáng"), SYSTEM("Hệ thống") }

/** Lưu tuỳ chọn giao diện và cờ onboarding trong SharedPreferences. */
class UiPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    val mode = mutableStateOf(runCatching { ThemeMode.valueOf(p.getString("mode", "DARK")!!) }.getOrDefault(ThemeMode.DARK))
    val dynamic = mutableStateOf(p.getBoolean("dynamic", false))
    val onboarded = mutableStateOf(p.getBoolean("onboarded", false))
    fun setMode(m: ThemeMode) { mode.value = m; p.edit().putString("mode", m.name).apply() }
    fun setDynamic(b: Boolean) { dynamic.value = b; p.edit().putBoolean("dynamic", b).apply() }
    fun finishOnboarding() { onboarded.value = true; p.edit().putBoolean("onboarded", true).apply() }
}

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.DARK, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx) }
        else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
