package io.github.omerek.keymapper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omerek.keymapper.input.LinuxKeys
import io.github.omerek.keymapper.model.KeyMapping
import io.github.omerek.keymapper.model.MappingRepository
import io.github.omerek.keymapper.shizuku.ShizukuManager
import io.github.omerek.keymapper.shizuku.ShizukuStatus
import kotlinx.coroutines.flow.first

@Composable
fun KeyboardCard(status: ShizukuStatus) {
    val context = LocalContext.current
    val devices by ShizukuManager.devices.collectAsStateWithLifecycle()
    val enabled by ShizukuManager.mappingEnabled.collectAsStateWithLifecycle()
    val mappings by MappingRepository.mappings.collectAsStateWithLifecycle()
    val (screenW, screenH) = remember { screenSize(context) }

    var lastKey by remember { mutableStateOf<Int?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var xText by remember { mutableStateOf((screenW / 2).toString()) }
    var yText by remember { mutableStateOf((screenH / 2).toString()) }

    LaunchedEffect(Unit) {
        ShizukuManager.keyEvents.collect { if (it.down) lastKey = it.code }
    }
    // "Tuşa bas" algılama: yakalama açıkken basılan ilk tuşa eşleme ata
    LaunchedEffect(capturing) {
        if (!capturing) return@LaunchedEffect
        val event = ShizukuManager.keyEvents.first { it.down }
        val x = xText.toFloatOrNull()
        val y = yText.toFloatOrNull()
        if (x != null && y != null) MappingRepository.upsert(KeyMapping(event.code, x, y))
        capturing = false
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("2. Klavye → dokunuş", fontWeight = FontWeight.Bold)
            status.nativeError?.let {
                Text("Yerel modül yüklenemedi: $it", color = MaterialTheme.colorScheme.error)
            }

            Text("Algılanan cihazlar", style = MaterialTheme.typography.labelLarge)
            if (devices.isEmpty()) {
                Text("Klavye veya mouse bulunamadı. OTG ile takıp birkaç saniye bekleyin.", style = MaterialTheme.typography.bodySmall)
            }
            devices.forEach { d ->
                val kind = listOfNotNull("klavye".takeIf { d.isKeyboard }, "mouse".takeIf { d.isMouse }).joinToString(", ")
                Text("• ${d.name} ($kind) ${d.path}", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Eşleme aktif")
                    Text(
                        "F12 ile de açılıp kapanır. Açıkken klavye yalnızca bu uygulamaya gelir.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { ShizukuManager.setMappingEnabled(it) })
            }
            Text("Son basılan tuş: ${lastKey?.let { "${LinuxKeys.name(it)} ($it)" } ?: "-"}")

            HorizontalDivider()
            Text("Eşlemeler", style = MaterialTheme.typography.labelLarge)
            if (mappings.isEmpty()) Text("Henüz eşleme yok.", style = MaterialTheme.typography.bodySmall)
            mappings.sortedBy { it.code }.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${LinuxKeys.name(m.code)} → (${m.x.toInt()}, ${m.y.toInt()})", Modifier.weight(1f))
                    TextButton(onClick = { MappingRepository.remove(m.code) }) { Text("Sil") }
                }
            }

            Text("Yeni eşleme (ekran $screenW × $screenH px)", style = MaterialTheme.typography.bodyMedium)
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
            if (capturing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Klavyede bir tuşa basın…", color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { capturing = false }) { Text("İptal") }
                }
            } else {
                Button(onClick = { capturing = true }) { Text("Tuşa basarak ekle") }
            }
        }
    }
}
