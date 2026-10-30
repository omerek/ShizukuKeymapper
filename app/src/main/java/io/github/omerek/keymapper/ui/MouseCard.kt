package io.github.omerek.keymapper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omerek.keymapper.model.MappingRepository
import java.util.Locale

@Composable
fun MouseCard() {
    val config by MappingRepository.mouseLook.collectAsStateWithLifecycle()
    // Kaydırırken her adımda kaydetmemek için yerel değer, bırakınca kaydedilir
    var sensitivity by remember(config.sensitivity) { mutableFloatStateOf(config.sensitivity) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("4. Mouse", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mouse ile kamera")
                    Text(
                        "Eşleme açıkken mouse hareketi kamera alanında sürüklemeye dönüşür. Alanın yerini ve " +
                            "büyüklüğünü editörde \"Kamera\" tutamacıyla ayarlayın.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { MappingRepository.setMouseLook(config.copy(enabled = it)) },
                )
            }
            Text("Hassasiyet: ${String.format(Locale.US, "%.2f", sensitivity)}×")
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                onValueChangeFinished = { MappingRepository.setMouseLook(config.copy(sensitivity = sensitivity)) },
                valueRange = 0.1f..5f,
            )
            Text(
                "Mouse tuşları (sol, sağ, orta, yan) editörde \"tuşa bas\" adımında mouse'a tıklanarak bir noktaya " +
                    "atanabilir. Eşleme kapalıyken (F12) mouse normal imleç olarak çalışır.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
