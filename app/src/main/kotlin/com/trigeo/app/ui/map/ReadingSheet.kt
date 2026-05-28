package com.trigeo.app.ui.map

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trigeo.app.domain.BearingCapture
import com.trigeo.app.domain.Defaults
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.ReadingDirection
import com.trigeo.app.geo.Angles
import com.trigeo.app.sensors.CompassAccuracy
import com.trigeo.app.sensors.CompassReading

enum class BearingMode { COMPASS, START_STOP, CUSTOM }

data class ReadingDraft(
    val name: String = "",
    val useGps: Boolean = true,
    val manualLat: String = "",
    val manualLon: String = "",
    val bearingMode: BearingMode = BearingMode.COMPASS,
    val manualBearingDeg: String = "",
    val startBearingText: String = "",
    val stopBearingText: String = "",
    val uncertaintyDeg: Float = Defaults.UNCERTAINTY_DEG.toFloat(),
    val direction: ReadingDirection = ReadingDirection.NORMAL,
)

private fun ReadingDraft.bearingFromCompass(liveCompass: CompassReading?): BearingCapture? {
    val center = liveCompass?.trueDeg ?: return null
    return BearingCapture.fromCenter(center, uncertaintyDeg.toDouble())
}

private fun ReadingDraft.bearingFromStartStop(): BearingCapture? {
    val s = startBearingText.toDoubleOrNull() ?: return null
    val e = stopBearingText.toDoubleOrNull() ?: return null
    return BearingCapture.fromStartStop(s, e)
}

private fun ReadingDraft.bearingFromCustom(): BearingCapture? {
    val center = manualBearingDeg.toDoubleOrNull() ?: return null
    return BearingCapture.fromCenter(center, uncertaintyDeg.toDouble())
}

data class DraftValues(
    val point: GeoPoint?,
    val bearing: BearingCapture?,
    val name: String?,
    val direction: ReadingDirection,
    val startBearingDeg: Double?,
    val stopBearingDeg: Double?,
)

fun ReadingDraft.toReadingValues(
    liveLocation: Location?,
    liveCompass: CompassReading?,
): DraftValues {
    val point: GeoPoint? = if (useGps) {
        liveLocation?.let { GeoPoint(it.latitude, it.longitude) }
    } else {
        val lat = manualLat.toDoubleOrNull()
        val lon = manualLon.toDoubleOrNull()
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            GeoPoint(lat, lon)
        } else null
    }
    val bearing = when (bearingMode) {
        BearingMode.COMPASS -> bearingFromCompass(liveCompass)
        BearingMode.START_STOP -> bearingFromStartStop()
        BearingMode.CUSTOM -> bearingFromCustom()
    }
    val (savedStart, savedStop) = if (bearingMode == BearingMode.START_STOP) {
        Pair(startBearingText.toDoubleOrNull(), stopBearingText.toDoubleOrNull())
    } else {
        Pair(null, null)
    }
    return DraftValues(
        point = point,
        bearing = bearing,
        name = name.trim().ifBlank { null },
        direction = direction,
        startBearingDeg = savedStart,
        stopBearingDeg = savedStop,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingPanel(
    title: String,
    initial: ReadingDraft,
    locationPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    liveLocation: Location?,
    liveCompass: CompassReading?,
    onSave: (DraftValues) -> Unit,
    onDelete: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(initial) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)

            if (!locationPermissionGranted) {
                PermissionBanner(onRequest = onRequestPermission)
            }

            LocationCard(
                draft = draft,
                liveLocation = liveLocation,
                onChange = { draft = it },
            )

            BearingCard(
                draft = draft,
                liveCompass = liveCompass,
                onChange = { draft = it },
            )

            if (draft.bearingMode != BearingMode.START_STOP) {
                UncertaintyCard(
                    value = draft.uncertaintyDeg,
                    onChange = { draft = draft.copy(uncertaintyDeg = it) },
                )
            }

            DirectionCard(
                direction = draft.direction,
                onChange = { draft = draft.copy(direction = it) },
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val values = draft.toReadingValues(liveLocation, liveCompass)
            val canSave = values.point != null && values.bearing != null

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
                if (onShare != null) {
                    OutlinedButton(onClick = onShare) { Text("Share") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = canSave,
                    onClick = { onSave(values) },
                ) { Text(if (onDelete != null) "Save" else "Add reading") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Location permission needed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Grant location to use your current GPS for the reading. You can still enter coordinates manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onRequest) { Text("Grant permission") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationCard(
    draft: ReadingDraft,
    liveLocation: Location?,
    onChange: (ReadingDraft) -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Location", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = draft.useGps,
                    onClick = { onChange(draft.copy(useGps = true)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Use GPS") }
                SegmentedButton(
                    selected = !draft.useGps,
                    onClick = { onChange(draft.copy(useGps = false)) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Custom") }
            }
            if (draft.useGps) {
                val text = liveLocation?.let { l ->
                    "%.6f, %.6f  ±%.0f m".format(l.latitude, l.longitude, l.accuracy)
                } ?: "Waiting for GPS fix..."
                Text(text, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.manualLat,
                        onValueChange = { onChange(draft.copy(manualLat = it)) },
                        label = { Text("Latitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.manualLon,
                        onValueChange = { onChange(draft.copy(manualLon = it)) },
                        label = { Text("Longitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BearingCard(
    draft: ReadingDraft,
    liveCompass: CompassReading?,
    onChange: (ReadingDraft) -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bearing", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = BearingMode.entries
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = draft.bearingMode == mode,
                        onClick = { onChange(draft.copy(bearingMode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) {
                        Text(
                            when (mode) {
                                BearingMode.COMPASS -> "Compass"
                                BearingMode.START_STOP -> "Start/Stop"
                                BearingMode.CUSTOM -> "Custom"
                            },
                        )
                    }
                }
            }
            when (draft.bearingMode) {
                BearingMode.COMPASS -> CompassReadout(liveCompass)
                BearingMode.START_STOP -> StartStopCapture(
                    draft = draft,
                    liveCompass = liveCompass,
                    onChange = onChange,
                )
                BearingMode.CUSTOM -> OutlinedTextField(
                    value = draft.manualBearingDeg,
                    onValueChange = { onChange(draft.copy(manualBearingDeg = it)) },
                    label = { Text("Bearing (degrees true)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CompassReadout(liveCompass: CompassReading?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                liveCompass?.let { "%.1f° true".format(it.trueDeg) } ?: "Waiting for compass...",
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            liveCompass?.let { CompassAccuracyBadge(it.accuracy) }
        }
        liveCompass?.let {
            Text(
                "Magnetic %.1f°  •  declination %+.1f°".format(it.magneticDeg, it.declinationDeg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (liveCompass != null && liveCompass.accuracy.needsCalibration) {
            CalibrationHint()
        }
    }
}

private val CompassAccuracy.needsCalibration: Boolean
    get() = this == CompassAccuracy.LOW ||
        this == CompassAccuracy.UNRELIABLE ||
        this == CompassAccuracy.NO_CONTACT

@Composable
private fun CompassAccuracyBadge(accuracy: CompassAccuracy) {
    val (label, color) = when (accuracy) {
        CompassAccuracy.HIGH -> "Cal: high" to androidx.compose.ui.graphics.Color(0xFF2E7D32)
        CompassAccuracy.MEDIUM -> "Cal: med" to androidx.compose.ui.graphics.Color(0xFF8E7700)
        CompassAccuracy.LOW -> "Cal: low" to androidx.compose.ui.graphics.Color(0xFFE57C00)
        CompassAccuracy.UNRELIABLE -> "Uncalibrated" to androidx.compose.ui.graphics.Color(0xFFC62828)
        CompassAccuracy.NO_CONTACT -> "No signal" to androidx.compose.ui.graphics.Color(0xFFC62828)
        CompassAccuracy.UNKNOWN -> "Cal: unknown" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun CalibrationHint() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            "Compass needs calibration. Hold the phone and wave it in a figure-8 a few times.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun StartStopCapture(
    draft: ReadingDraft,
    liveCompass: CompassReading?,
    onChange: (ReadingDraft) -> Unit,
) {
    val startVal = draft.startBearingText.toDoubleOrNull()
    val stopVal = draft.stopBearingText.toDoubleOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Sweep through the signal. Mark the heading where you first hear it, then where it fades. You can also type values directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                liveCompass?.let { "%.1f° true".format(it.trueDeg) } ?: "Waiting for compass...",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            liveCompass?.let { CompassAccuracyBadge(it.accuracy) }
        }
        if (liveCompass != null && liveCompass.accuracy.needsCalibration) {
            CalibrationHint()
        }
        StartStopField(
            label = "Start",
            value = draft.startBearingText,
            onValueChange = { onChange(draft.copy(startBearingText = it)) },
            onMarkFromCompass = liveCompass?.let { c ->
                { onChange(draft.copy(startBearingText = "%.1f".format(c.trueDeg))) }
            },
        )
        StartStopField(
            label = "Stop",
            value = draft.stopBearingText,
            onValueChange = { onChange(draft.copy(stopBearingText = it)) },
            onMarkFromCompass = liveCompass?.let { c ->
                { onChange(draft.copy(stopBearingText = "%.1f".format(c.trueDeg))) }
            },
        )
        if (startVal != null && stopVal != null) {
            val r = Angles.bisector(startVal, stopVal)
            Text(
                "Bisector: %.1f° ± %.1f°".format(r.centerDeg, r.halfWidthDeg),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun StartStopField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onMarkFromCompass: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = { onMarkFromCompass?.invoke() },
            enabled = onMarkFromCompass != null,
        ) { Text("Mark") }
    }
}

@Composable
private fun DirectionCard(
    direction: ReadingDirection,
    onChange: (ReadingDirection) -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Direction", style = MaterialTheme.typography.titleMedium)
            Text(
                "How the bearing is drawn and used. Reversed flips it 180 degrees (e.g. navigating off a back null) while keeping the captured value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DirectionOption("Normal", "Forward only.", direction == ReadingDirection.NORMAL) {
                onChange(ReadingDirection.NORMAL)
            }
            DirectionOption(
                "Bidirectional",
                "Both ways. For null antennas with a 180-degree ambiguity.",
                direction == ReadingDirection.BIDIRECTIONAL,
            ) { onChange(ReadingDirection.BIDIRECTIONAL) }
            DirectionOption(
                "Reversed",
                "Opposite of the captured heading.",
                direction == ReadingDirection.REVERSED,
            ) { onChange(ReadingDirection.REVERSED) }
        }
    }
}

@Composable
private fun DirectionOption(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UncertaintyCard(
    value: Float,
    onChange: (Float) -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Uncertainty", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "%.0f°".format(value),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 1f..30f,
                steps = 28,
            )
        }
    }
}
