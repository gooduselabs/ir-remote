package vn.irremote.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var recognizer: SpeechRecognizer? = null
    val listening = mutableStateOf(false)
    private val micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startVoice() else vm.messages.tryEmit("Cần quyền micro để dùng giọng nói") }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = UiPrefs(this)
        setContent {
            AppTheme(prefs.mode.value, prefs.dynamic.value) {
                if (!prefs.onboarded.value) { OnboardingContent(vm.irAvailable, prefs::finishOnboarding); return@AppTheme }
                val nav = rememberNavController()
                val ease = tween<IntOffset>(320, easing = FastOutSlowInEasing)
                NavHost(nav, "home", Modifier.background(MaterialTheme.colorScheme.background),
                    enterTransition = { slideInHorizontally(ease) { it / 4 } + fadeIn(tween(320)) },
                    exitTransition = { slideOutHorizontally(ease) { -it / 6 } + fadeOut(tween(200)) },
                    popEnterTransition = { slideInHorizontally(ease) { -it / 6 } + fadeIn(tween(320)) },
                    popExitTransition = { slideOutHorizontally(ease) { it / 4 } + fadeOut(tween(200)) }) {
                    composable("home") { HomeScreen(vm, nav, prefs, onMic = ::requestVoice, listening = listening.value) }
                    composable("remote/{id}") { RemoteScreen(vm, it.arguments!!.getString("id")!!.toLong(), nav) }
                    composable("wizard") { WizardScreen(vm, nav) }
                    composable("macro/{id}") { MacroEditor(vm, it.arguments!!.getString("id")!!.toLong(), nav) }
                }
            }
        }
    }

    fun requestVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { vm.messages.tryEmit("Máy không hỗ trợ nhận dạng giọng nói"); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    listening.value = false
                    r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { vm.voice(it) }
                }
                override fun onError(e: Int) { listening.value = false; vm.messages.tryEmit("Không nghe rõ, thử lại (mã $e)") }
                override fun onReadyForSpeech(p: Bundle?) { listening.value = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            })
        }
    }
    override fun onDestroy() { recognizer?.destroy(); super.onDestroy() }
}
