package com.trigeo.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.trigeo.app.data.RegionProgress
import com.trigeo.app.map.MapTileStyle
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.min

@Composable
fun DownloadRegionDialog(
    bounds: LatLngBounds,
    tileStyle: MapTileStyle,
    progress: RegionProgress?,
    onConfirm: (name: String, minZoom: Double, maxZoom: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("Region") }
    var minZoom by remember { mutableStateOf(11f) }
    var maxZoom by remember { mutableStateOf(min(16f, tileStyle.maxOfflineZoom.toFloat())) }
    val isActive = progress is RegionProgress.InFlight || progress is RegionProgress.Started
    val isComplete = progress is RegionProgress.Complete
    val failed = progress as? RegionProgress.Failed

    AlertDialog(
        onDismissRequest = { if (!isActive) onDismiss() },
        title = { Text(if (isComplete) "Download complete" else "Download offline region") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isComplete) {
                    val c = progress as RegionProgress.Complete
                    Text("${c.tiles} tiles, ${formatBytes(c.bytes)}.")
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Region name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isActive,
                    )
                    Text("Style: ${tileStyle.displayName}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Bounds: %.4f, %.4f → %.4f, %.4f".format(
                            bounds.latitudeSouth, bounds.longitudeWest, bounds.latitudeNorth, bounds.longitudeEast,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    ZoomSlider(
                        label = "Min zoom",
                        value = minZoom,
                        onChange = { minZoom = it.coerceAtMost(maxZoom) },
                        range = 0f..tileStyle.maxOfflineZoom.toFloat(),
                        enabled = !isActive,
                    )
                    ZoomSlider(
                        label = "Max zoom",
                        value = maxZoom,
                        onChange = { maxZoom = it.coerceAtLeast(minZoom) },
                        range = 0f..tileStyle.maxOfflineZoom.toFloat(),
                        enabled = !isActive,
                    )
                    val tiles = estimatedTileCount(bounds, minZoom.toDouble(), maxZoom.toDouble())
                    Text(
                        "Estimated tiles: $tiles (~${formatBytes(tiles * 15_000L)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isActive) {
                    val p = progress as? RegionProgress.InFlight
                    val fraction = if (p != null && p.requiredTiles > 0) {
                        (p.completedTiles.toFloat() / p.requiredTiles.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (p != null) {
                        Text(
                            "${p.completedTiles} / ${p.requiredTiles} tiles  •  ${formatBytes(p.bytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        Text("Starting...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                failed?.let {
                    Text(it.reason, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when {
                isComplete || failed != null -> {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                isActive -> {
                    TextButton(onClick = {}, enabled = false) { Text("Downloading...") }
                }
                else -> {
                    TextButton(onClick = {
                        onConfirm(name.trim().ifBlank { "Region" }, minZoom.toDouble(), maxZoom.toDouble())
                    }) { Text("Download") }
                }
            }
        },
        dismissButton = {
            if (!isActive && !isComplete) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ZoomSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
        Text(
            "%.0f".format(value),
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun estimatedTileCount(bounds: LatLngBounds, minZoom: Double, maxZoom: Double): Long {
    var sum = 0L
    val n = ((maxZoom - minZoom).toInt() + 1).coerceAtLeast(1)
    var z = minZoom.toInt()
    repeat(n) {
        val nTiles = 1 shl z
        val xMin = lonToX(bounds.longitudeWest, z)
        val xMax = lonToX(bounds.longitudeEast, z)
        val yMin = latToY(bounds.latitudeNorth, z)
        val yMax = latToY(bounds.latitudeSouth, z)
        val w = ((xMax - xMin + nTiles) % nTiles) + 1
        val h = (yMax - yMin) + 1
        sum += w.toLong() * h.toLong()
        z += 1
    }
    return sum
}

private fun lonToX(lon: Double, z: Int): Int {
    val n = 1 shl z
    val x = ((lon + 180.0) / 360.0 * n).toInt()
    return x.coerceIn(0, n - 1)
}

private fun latToY(lat: Double, z: Int): Int {
    val n = 1 shl z
    val latRad = Math.toRadians(lat)
    val y = ((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
    return y.coerceIn(0, n - 1)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
