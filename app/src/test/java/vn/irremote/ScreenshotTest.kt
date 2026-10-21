package vn.irremote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.irremote.ac.AcFan
import vn.irremote.ac.AcMode
import vn.irremote.ac.AcState
import vn.irremote.data.*
import vn.irremote.ui.*

/** Chụp màn hình các màn chính (dữ liệu mẫu) bằng Roborazzi, lưu vào ../screens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val dir = System.getProperty("screens.dir") ?: "build/screens"

    private val rooms = listOf(RoomEntity(1, "Phòng khách"), RoomEntity(2, "Phòng ngủ"))
    private val remotes = listOf(
        RemoteEntity(1, "Samsung TV", "TV", "Samsung", 1, favorite = true),
        RemoteEntity(2, "Daikin Điều hoà", "AC", "Daikin", 2, favorite = true, acState = """{"power":true,"mode":"COOL","temp":24,"fan":"AUTO"}"""),
        RemoteEntity(3, "Quạt Dyson", "FAN", "Dyson", 2),
        RemoteEntity(4, "Đầu thu VTVcab", "STB", "Tesla", 1),
        RemoteEntity(5, "Máy chiếu Epson", "PROJECTOR", "Epson", 1),
    )
    private val tvButtons = (listOf("Power", "Source", "Home", "Menu", "Up", "Down", "Left", "Right", "Ok", "Back", "Info", "Vol_up", "Vol_dn", "Mute", "Ch_next", "Ch_prev") +
        (0..9).map { "$it" } + listOf("Netflix", "YouTube", "Sleep", "Picture")).mapIndexed { i, n -> ButtonEntity(i.toLong() + 1, 1, n, "Samsung32", 7, i.toLong()) }
    private val macros = listOf(MacroEntity(1, "Xem phim", """[{"buttonId":1},{"buttonId":2},{"buttonId":3}]"""),
        MacroEntity(2, "Đi ngủ", """[{"buttonId":1},{"buttonId":4}]"""), MacroEntity(3, "Về nhà", """[{"buttonId":5}]"""))

    private fun shot(name: String, mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        rule.setContent { AppTheme(mode) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("$dir/$name.png")
    }

    @Composable private fun Home(tab: Int) = HomeContent(tab, {}, true, 0, false, {}, remember { SnackbarHostState() }, {}, {}) {
        when (it) {
            0 -> DevicesContent(remotes, rooms, {}, {}, acOf = { r -> runCatching { App.json.decodeFromString<AcState>(r.acState) }.getOrDefault(AcState()) }) {}
            1 -> ScenesContent(macros, {}, {}, {})
            else -> SettingsContent(ThemeMode.DARK, {}, false, {}, true, "30–60 kHz", rooms, {}, {}, {}, {})
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Frame(title: String, sub: String, body: @Composable () -> Unit) = Scaffold(topBar = {
        TopAppBar(title = { Column2(title, sub) }, navigationIcon = { IconButton({}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { IrLed(0); IconButton({}) { Icon(Icons.Default.Star, null, tint = Brand.Warm) }; IconButton({}) { Icon(Icons.Default.MoreVert, null) } })
    }) { p -> Box(Modifier.padding(p)) { body() } }

    @Composable private fun Column2(t: String, s: String) = androidx.compose.foundation.layout.Column {
        Text(t, style = MaterialTheme.typography.titleLarge); Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    @Test fun home_dark() = shot("01_home_dark") { Home(0) }
    @Test fun home_light() = shot("02_home_light", ThemeMode.LIGHT) { Home(0) }
    @Test fun home_empty() = shot("03_home_empty") { HomeContent(0, {}, false, 0, false, {}, remember { SnackbarHostState() }, {}, {}) { DevicesContent(emptyList(), emptyList(), {}, {}) {} } }
    @Test fun tv_remote() = shot("04_tv_remote_dark") { Frame("Samsung TV", "TV · Samsung") { RemoteContent(tvButtons, {}, {}, {}) } }
    @Test fun ac() = shot("05_ac_dark") {
        Frame("Daikin Điều hoà", "Điều hoà · Daikin") { AcContent(AcState(true, AcMode.COOL, 24, AcFan.AUTO), "Cool_24_auto", tvButtons.take(12), {}, {}, {}) }
    }
    @Test fun ac_light() = shot("06_ac_light", ThemeMode.LIGHT) {
        Frame("Daikin Điều hoà", "Điều hoà · Daikin") { AcContent(AcState(true, AcMode.HEAT, 28, AcFan.HIGH), null, tvButtons.take(12), {}, {}, {}) }
    }
    @Test fun wizard_type() = shot("07_wizard_type_dark") {
        WizardFrame(0, remember { SnackbarHostState() }, {}) { WizardTypeStep(mapOf(DeviceType.TV to 19, DeviceType.STB to 2, DeviceType.FAN to 8, DeviceType.AC to 7, DeviceType.PROJECTOR to 12)) {} }
    }
    @Test fun wizard_test() = shot("08_wizard_test_dark") {
        WizardFrame(2, remember { SnackbarHostState() }, {}) { WizardTestStep(DeviceType.TV, "Samsung", "Samsung AA59-00484A", 42, 1, 3, "Power", true, {}, {}, {}, {}) }
    }
    @Test fun scenes() = shot("09_scenes_dark") { Home(1) }
    @Test fun settings() = shot("10_settings_dark") { Home(2) }
    @Test fun onboarding() = shot("11_onboarding_dark") { OnboardingContent(true) {} }
}
