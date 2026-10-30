package io.github.omerek.keymapper.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.omerek.keymapper.input.LinuxKeys
import io.github.omerek.keymapper.model.KeyMapping
import io.github.omerek.keymapper.shizuku.ShizukuManager
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** Editörde düzenlenen nokta. code == null: henüz tuş atanmamış. x, y ekran pikseli. */
private data class EditPoint(val id: Int, val code: Int?, val x: Float, val y: Float)

private val MARKER_SIZE = 56.dp

/**
 * Tam ekran yarı saydam editör.
 * - Boş yere dokun: o noktaya yeni nokta ekler ve tuş bekler.
 * - Noktayı sürükle: taşır. Noktaya dokun: seçer (tuş değiştir / sil).
 * - Tuş beklerken Esc: iptal.
 */
@Composable
fun EditorOverlay(
    initial: List<KeyMapping>,
    onSave: (List<KeyMapping>) -> Unit,
    onCancel: () -> Unit,
) {
    val view = LocalView.current
    val markerPx = with(LocalDensity.current) { MARKER_SIZE.toPx() }

    // Pencerenin ekrandaki konumu: noktalar ekran koordinatında saklanır
    var origin by remember { mutableStateOf(Offset.Zero) }
    val points = remember {
        mutableStateListOf<EditPoint>().apply {
            addAll(initial.mapIndexed { i, m -> EditPoint(i, m.code, m.x, m.y) })
        }
    }
    var nextId by remember { mutableIntStateOf(initial.size) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var awaiting by remember { mutableStateOf<Int?>(null) }

    fun indexOf(id: Int) = points.indexOfFirst { it.id == id }

    fun dropPendingPoint() {
        val id = awaiting ?: return
        val i = indexOf(id)
        if (i >= 0 && points[i].code == null) points.removeAt(i)
        awaiting = null
    }

    // "Tuşa bas" algılama
    LaunchedEffect(awaiting) {
        val id = awaiting ?: return@LaunchedEffect
        val event = ShizukuManager.keyEvents.first { it.down }
        if (event.code == LinuxKeys.KEY_ESC) {
            dropPendingPoint()
            return@LaunchedEffect
        }
        // Bir tuş tek noktaya bağlı olabilir
        points.removeAll { it.code == event.code && it.id != id }
        val i = indexOf(id)
        if (i >= 0) points[i] = points[i].copy(code = event.code)
        awaiting = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .onGloballyPositioned {
                val loc = IntArray(2).also { view.getLocationOnScreen(it) }
                origin = Offset(loc[0].toFloat(), loc[1].toFloat())
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    dropPendingPoint()
                    val id = nextId++
                    points.add(EditPoint(id, null, pos.x + origin.x, pos.y + origin.y))
                    selected = id
                    awaiting = id
                }
            },
    ) {
        points.forEach { p ->
            key(p.id) {
                PointMarker(
                    point = p,
                    origin = origin,
                    markerPx = markerPx,
                    selected = selected == p.id,
                    waiting = awaiting == p.id,
                    onSelect = { selected = p.id },
                    onDrag = { dx, dy ->
                        val i = indexOf(p.id)
                        if (i >= 0) points[i] = points[i].copy(x = points[i].x + dx, y = points[i].y + dy)
                    },
                )
            }
        }

        Toolbar(
            modifier = Modifier.align(Alignment.TopCenter),
            hint = when {
                awaiting != null -> "Klavyede bir tuşa basın (Esc: iptal)"
                else -> "Boş yere dokun: nokta ekle • Sürükle: taşı • Dokun: seç"
            },
            hasSelection = selected != null && awaiting == null,
            onReassign = { selected?.let { awaiting = it } },
            onDelete = {
                selected?.let { id -> indexOf(id).takeIf { it >= 0 }?.let { points.removeAt(it) } }
                selected = null
            },
            onCancel = onCancel,
            onSave = {
                onSave(points.mapNotNull { p -> p.code?.let { KeyMapping(it, p.x, p.y) } })
            },
        )
    }
}

@Composable
private fun PointMarker(
    point: EditPoint,
    origin: Offset,
    markerPx: Float,
    selected: Boolean,
    waiting: Boolean,
    onSelect: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val color = when {
        waiting -> Color(0xFFFFA000)
        point.code == null -> Color(0xFF757575)
        else -> Color(0xFF1E88E5)
    }
    Box(
        Modifier
            .offset {
                IntOffset(
                    (point.x - origin.x - markerPx / 2).roundToInt(),
                    (point.y - origin.y - markerPx / 2).roundToInt(),
                )
            }
            .size(MARKER_SIZE)
            .background(color.copy(alpha = 0.8f), CircleShape)
            .border(if (selected) 3.dp else 1.dp, Color.White, CircleShape)
            .pointerInput(point.id) {
                detectDragGestures(onDragStart = { onSelect() }) { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            }
            .pointerInput(point.id) { detectTapGestures { onSelect() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = point.code?.let { LinuxKeys.name(it) } ?: "?",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun Toolbar(
    modifier: Modifier,
    hint: String,
    hasSelection: Boolean,
    onReassign: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        // Araç çubuğuna dokunuş alttaki "nokta ekle" alanına geçmesin
        modifier = modifier
            .padding(top = 24.dp)
            .pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(hint, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasSelection) {
                    OutlinedButton(onClick = onReassign) { Text("Tuş değiştir") }
                    OutlinedButton(onClick = onDelete) { Text("Sil") }
                }
                OutlinedButton(onClick = onCancel) { Text("İptal") }
                Button(onClick = onSave) { Text("Kaydet") }
            }
        }
    }
}
