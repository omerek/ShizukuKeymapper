package io.github.omerek.keymapper.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.omerek.keymapper.overlay.OverlayService
import io.github.omerek.keymapper.shizuku.ShizukuManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlayCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var grantLog by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        canDraw = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("3. Overlay editörü", fontWeight = FontWeight.Bold)
            if (!canDraw) {
                Text("Oyunun üstünde katman açmak için \"diğer uygulamaların üzerinde göster\" izni gerekiyor.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        grantLog = ShizukuManager.grantAppPermissions()
                        scope.launch {
                            delay(500)
                            canDraw = Settings.canDrawOverlays(context)
                        }
                    }) { Text("İzni Shizuku ile ver") }
                    OutlinedButton(onClick = { openOverlaySettings(context) }) { Text("Ayarlardan ver") }
                }
                grantLog?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            } else {
                Text(
                    "Simge (KM) oyunun üstünde durur: dokun = editör, sürükle = taşı. Yeşil \"AÇIK\" = eşleme aktif (F12).",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startOverlay(context, OverlayService.ACTION_SHOW) }) { Text("Simgeyi göster") }
                    OutlinedButton(onClick = { startOverlay(context, OverlayService.ACTION_EDIT) }) { Text("Editörü aç") }
                    OutlinedButton(onClick = {
                        context.stopService(Intent(context, OverlayService::class.java))
                    }) { Text("Kapat") }
                }
            }
        }
    }
}

private fun startOverlay(context: Context, action: String) {
    ContextCompat.startForegroundService(context, OverlayService.intent(context, action))
}

private fun openOverlaySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    )
}
