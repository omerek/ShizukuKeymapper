package io.github.omerek.keymapper.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omerek.keymapper.shizuku.PermissionState
import io.github.omerek.keymapper.shizuku.ShizukuManager
import io.github.omerek.keymapper.shizuku.ShizukuStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val status by ShizukuManager.status.collectAsStateWithLifecycle()

    // İzin verildiği anda servisi otomatik bağla
    LaunchedEffect(status.ready) {
        if (status.ready && !status.serviceConnected) ShizukuManager.bindService()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Shizuku Keymapper", style = MaterialTheme.typography.headlineSmall)
            ShizukuCard(status)
            if (status.serviceConnected) {
                KeyboardCard(status)
                OverlayCard()
                TouchTestCard()
            }
        }
    }
}

@Composable
private fun ShizukuCard(status: ShizukuStatus) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("1. Shizuku bağlantısı", fontWeight = FontWeight.Bold)
            StatusRow("Shizuku kurulu", status.installed)
            StatusRow("Shizuku çalışıyor", status.running, if (status.version > 0) "API v${status.version}" else null)
            if (status.tooOld) Text("Shizuku sürümü çok eski, lütfen güncelleyin.", color = MaterialTheme.colorScheme.error)
            StatusRow("İzin verildi", status.permission == PermissionState.GRANTED)
            StatusRow(
                "Servis bağlı",
                status.serviceConnected,
                if (status.serviceUid >= 0) "uid ${status.serviceUid}${if (status.serviceUid == 2000) " (shell)" else ""}" else null,
            )
            status.error?.let { Text("Hata: $it", color = MaterialTheme.colorScheme.error) }

            // Sıradaki adıma yönlendirme
            when {
                !status.installed -> {
                    Text("Shizuku kurulu değil. Play Store'dan veya GitHub'dan kurun.")
                    Button(onClick = { openShizukuDownload(context) }) { Text("Shizuku'yu indir") }
                }
                !status.running -> {
                    Text(
                        "Shizuku çalışmıyor. Shizuku uygulamasını açıp \"Kablosuz hata ayıklama ile başlat\" " +
                            "adımlarını izleyin, sonra buraya dönün."
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openShizuku(context) }) { Text("Shizuku'yu aç") }
                        OutlinedButton(onClick = { ShizukuManager.refresh() }) { Text("Yenile") }
                    }
                }
                status.permission == PermissionState.NOT_REQUESTED -> {
                    Button(onClick = { ShizukuManager.requestPermission() }) { Text("Shizuku izni iste") }
                }
                status.permission == PermissionState.DENIED_PERMANENTLY -> {
                    Text("İzin kalıcı olarak reddedildi. Shizuku > Yetkili uygulamalar bölümünden bu uygulamaya izin verin.")
                    Button(onClick = { openShizuku(context) }) { Text("Shizuku'yu aç") }
                }
                !status.serviceConnected -> {
                    Button(onClick = { ShizukuManager.bindService() }) { Text("Servisi başlat") }
                }
                else -> {
                    OutlinedButton(onClick = { ShizukuManager.unbindService() }) { Text("Servisi durdur") }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, detail: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "❌")
        Text("  $label", Modifier.weight(1f))
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun TouchTestCard() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val (screenW, screenH) = remember { screenSize(context) }

    var xText by remember { mutableStateOf((screenW / 2).toString()) }
    var yText by remember { mutableStateOf((screenH / 2).toString()) }
    var countdown by remember { mutableIntStateOf(0) }
    var hits by remember { mutableIntStateOf(0) }
    var targetCenter by remember { mutableStateOf(Offset.Zero) }
    var log by remember { mutableStateOf("") }

    fun service() = ShizukuManager.service

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("2. Dokunuş enjeksiyonu testi", fontWeight = FontWeight.Bold)
            Text("Ekran: $screenW × $screenH px", style = MaterialTheme.typography.bodySmall)

            // Uygulama içi hedef: enjekte edilen dokunuş buraya gelirse sayaç artar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .clickable { hits++ }
                    .onGloballyPositioned { coords ->
                        val loc = IntArray(2).also { view.getLocationOnScreen(it) }
                        val c = coords.boundsInWindow().center
                        targetCenter = Offset(c.x + loc[0], c.y + loc[1])
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Hedef alan • dokunuş sayısı: $hits")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    service()?.tap(targetCenter.x, targetCenter.y, 50)
                    log = "Hedefe dokunuldu: (${targetCenter.x.toInt()}, ${targetCenter.y.toInt()})"
                }) { Text("Hedefe dokun") }
                OutlinedButton(onClick = {
                    service()?.swipe(targetCenter.x - 200, targetCenter.y, targetCenter.x + 200, targetCenter.y, 300)
                    log = "Kaydırma gönderildi"
                }) { Text("Kaydırma testi") }
            }

            Text("Başka bir uygulamada denemek için:", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    xText, { xText = it }, Modifier.width(120.dp), label = { Text("X") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                OutlinedTextField(
                    yText, { yText = it }, Modifier.width(120.dp), label = { Text("Y") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
            }
            Button(
                enabled = countdown == 0,
                onClick = {
                    val x = xText.toFloatOrNull() ?: return@Button
                    val y = yText.toFloatOrNull() ?: return@Button
                    scope.launch {
                        for (i in 5 downTo 1) {
                            countdown = i
                            delay(1000)
                        }
                        countdown = 0
                        service()?.tap(x, y, 50)
                        log = "($x, $y) noktasına dokunuldu"
                    }
                },
            ) { Text(if (countdown > 0) "$countdown sn…" else "5 sn sonra (X,Y)'ye dokun") }
            if (log.isNotEmpty()) Text(log, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun screenSize(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(WindowManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val b = wm.currentWindowMetrics.bounds
        b.width() to b.height()
    } else {
        val p = android.graphics.Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(p)
        p.x to p.y
    }
}

private fun openShizuku(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
    if (intent != null) context.startActivity(intent) else openShizukuDownload(context)
}

private fun openShizukuDownload(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuManager.SHIZUKU_PACKAGE}"))
    try {
        context.startActivity(market)
    } catch (e: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
    }
}
