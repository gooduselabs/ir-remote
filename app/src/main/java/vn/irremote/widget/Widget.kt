package vn.irremote.widget

import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.irremote.App

private val KEY = ActionParameters.Key<Long>("buttonId")

/** Widget màn hình chính: tối đa 6 nút yêu thích, bấm là phát IR không cần mở app. */
class FavWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val buttons = App.instance.db.dao().favoriteButtons().take(6)
        provideContent {
            Column(GlanceModifier.fillMaxSize().padding(6.dp).background(androidx.glance.color.ColorProvider(day = androidx.compose.ui.graphics.Color(0xFFEFF3FA), night = androidx.compose.ui.graphics.Color(0xFF1E2430)))) {
                if (buttons.isEmpty()) Text("Nhấn giữ một nút trong app → \"Thêm vào widget\"")
                buttons.chunked(3).forEach { row ->
                    Row(GlanceModifier.fillMaxWidth()) {
                        row.forEach { b -> Button(b.label.replace('_', ' ').take(10), actionRunCallback<SendAction>(actionParametersOf(KEY to b.id)), GlanceModifier.defaultWeight().padding(2.dp)) }
                    }
                }
            }
        }
    }
}

class SendAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[KEY] ?: return
        App.instance.db.dao().button(id)?.let { App.instance.sendButton(it) }
    }
}

class FavWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = FavWidget() }

/** Ô Cài đặt nhanh: phát nút yêu thích đầu tiên (thường là Nguồn TV). */
class IrTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onStartListening() {
        scope.launch {
            val b = App.instance.db.dao().favoriteButtons().firstOrNull()
            qsTile?.apply { label = b?.label?.replace('_', ' ') ?: "IR Remote"; state = if (b == null) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE; updateTile() }
        }
    }
    override fun onClick() {
        scope.launch {
            val b = App.instance.db.dao().favoriteButtons().firstOrNull() ?: return@launch
            App.instance.sendButton(b)
        }
    }
}
