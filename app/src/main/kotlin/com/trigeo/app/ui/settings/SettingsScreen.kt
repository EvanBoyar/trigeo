package com.trigeo.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trigeo.app.domain.Defaults
import com.trigeo.app.domain.ReadingDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAddRegion: () -> Unit,
) {
    val defaultDirection by viewModel.defaultDirection.collectAsState()
    val defaultUncertaintyDeg by viewModel.defaultUncertaintyDeg.collectAsState()
    val minFixRangeMeters by viewModel.minFixRangeMeters.collectAsState()
    val tipButtonEnabled by viewModel.tipButtonEnabled.collectAsState()
    val defaultStartStopMode by viewModel.defaultStartStopMode.collectAsState()
    val offlineRegions by viewModel.offlineRegions.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DirectionCard(
                direction = defaultDirection,
                onChange = viewModel::setDefaultDirection,
            )

            SliderRow(
                title = "Default uncertainty",
                subtitle = "Starting half-cone width for new readings. Per-reading values can still be set in the capture panel.",
                value = defaultUncertaintyDeg,
                valueLabel = "%.0f°".format(defaultUncertaintyDeg),
                valueRange = Defaults.UNCERTAINTY_MIN_DEG.toFloat()..Defaults.UNCERTAINTY_MAX_DEG.toFloat(),
                steps = (Defaults.UNCERTAINTY_MAX_DEG - Defaults.UNCERTAINTY_MIN_DEG).toInt() - 1,
                defaultValue = Defaults.UNCERTAINTY_DEG.toFloat(),
                editTitle = "Default uncertainty",
                editSuffix = "°",
                onChange = viewModel::setDefaultUncertaintyDeg,
            )
            SliderRow(
                title = "Close-range floor",
                subtitle = "Closer readings count for more when triangulating. This floor stops a single very close bearing, where the antenna often swings, from taking over the fix.",
                value = minFixRangeMeters,
                valueLabel = "%.0f m".format(minFixRangeMeters),
                valueRange = 5f..50f,
                steps = 8,
                defaultValue = Defaults.MIN_FIX_RANGE_METERS.toFloat(),
                editTitle = "Close-range floor",
                editSuffix = " m",
                onChange = viewModel::setMinFixRangeMeters,
            )
            SwitchRow(
                title = "Start/stop quick capture",
                subtitle = "When on, Quick add takes two taps: the first when the signal first appears, the second when it disappears. The Add reading panel also defaults to start/stop.",
                checked = defaultStartStopMode,
                onChange = viewModel::setDefaultStartStopMode,
            )
            OfflineRegionsCard(
                regions = offlineRegions,
                onDelete = viewModel::deleteRegion,
                onAddRegion = onAddRegion,
            )
            SwitchRow(
                title = "Show tip button",
                subtitle = "A small heart button on the home screen that opens a tip page.",
                checked = tipButtonEnabled,
                onChange = viewModel::setTipButtonEnabled,
            )
        }
    }
}

@Composable
private fun OfflineRegionsCard(
    regions: List<com.trigeo.app.data.OfflineRegionInfo>,
    onDelete: (Long) -> Unit,
    onAddRegion: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Offline map regions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = onAddRegion) {
                    Text("Add region")
                }
            }
            if (regions.isEmpty()) {
                Text(
                    "Nothing saved yet. Tap Add region above to pick an area on the map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                regions.forEach { region ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(region.name, style = MaterialTheme.typography.titleSmall)
                            val zoom = "z%.0f-%.0f".format(region.minZoom, region.maxZoom)
                            val size = formatBytes(region.downloadedBytes)
                            val status = if (region.isComplete) "complete" else
                                "${region.downloadedTiles}/${region.requiredTiles}"
                            Text(
                                "$zoom  •  $size  •  $status",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = { onDelete(region.id) }) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete region",
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun DirectionCard(
    direction: ReadingDirection,
    onChange: (ReadingDirection) -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Default reading direction", style = MaterialTheme.typography.titleMedium)
            Text(
                "How new readings are drawn and used. Reversed flips the bearing 180 degrees (e.g. navigating off a back null) while keeping the captured value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DirectionOption(
                label = "Normal",
                subtitle = "Forward only.",
                selected = direction == ReadingDirection.NORMAL,
                onClick = { onChange(ReadingDirection.NORMAL) },
            )
            DirectionOption(
                label = "Bidirectional",
                subtitle = "Both ways. For null antennas with a 180-degree ambiguity.",
                selected = direction == ReadingDirection.BIDIRECTIONAL,
                onClick = { onChange(ReadingDirection.BIDIRECTIONAL) },
            )
            DirectionOption(
                label = "Reversed",
                subtitle = "Opposite of the captured heading.",
                selected = direction == ReadingDirection.REVERSED,
                onClick = { onChange(ReadingDirection.REVERSED) },
            )
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
        RadioButton(selected = selected, onClick = onClick)
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
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
internal fun SliderRow(
    title: String,
    subtitle: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    defaultValue: Float?,
    editTitle: String,
    editSuffix: String,
    onChange: (Float) -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EditableValueLabel(
                    label = valueLabel,
                    title = editTitle,
                    current = value,
                    range = valueRange,
                    suffix = editSuffix,
                    onChange = onChange,
                )
            }
            Spacer(Modifier.height(4.dp))
            SliderWithDefaultTick(
                value = value,
                onValueChange = onChange,
                valueRange = valueRange,
                steps = steps,
                defaultValue = defaultValue,
            )
        }
    }
}

@Composable
internal fun EditableValueLabel(
    label: String,
    title: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onChange: (Float) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { editing = true }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
    if (editing) {
        NumberEditDialog(
            title = title,
            current = current,
            range = range,
            suffix = suffix,
            onDismiss = { editing = false },
            onConfirm = {
                onChange(it)
                editing = false
            },
        )
    }
}

@Composable
internal fun NumberEditDialog(
    title: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var text by remember { mutableStateOf("%.0f".format(current)) }
    val parsed = text.replace(',', '.').toFloatOrNull()
    val valid = parsed != null && parsed in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text(suffix.trim()) },
                    isError = !valid,
                )
                Text(
                    "Range: %.0f to %.0f$suffix".format(range.start, range.endInclusive),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { parsed?.let(onConfirm) },
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun SliderWithDefaultTick(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    defaultValue: Float?,
) {
    val tickColor = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
        if (defaultValue != null && defaultValue in valueRange) {
            val span = valueRange.endInclusive - valueRange.start
            val fraction = if (span > 0f) (defaultValue - valueRange.start) / span else 0f
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 2.dp),
            ) {
                val x = fraction * size.width
                val y = size.height / 2f
                drawCircle(
                    color = tickColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }
    }
}
