package com.trigeo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAddRegion: () -> Unit,
) {
    val defaultBidirectional by viewModel.defaultBidirectional.collectAsState()
    val defaultUncertaintyDeg by viewModel.defaultUncertaintyDeg.collectAsState()
    val tipButtonEnabled by viewModel.tipButtonEnabled.collectAsState()
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwitchRow(
                title = "Default to bidirectional readings",
                subtitle = "New readings start with the bidirectional toggle on. Useful for null-based antennas with a 180-degree ambiguity.",
                checked = defaultBidirectional,
                onChange = viewModel::setDefaultBidirectional,
            )

            SliderRow(
                title = "Default uncertainty",
                subtitle = "Starting half-cone width for new readings. Per-reading values can still be set in the capture panel.",
                value = defaultUncertaintyDeg,
                valueLabel = "%.0f°".format(defaultUncertaintyDeg),
                valueRange = 1f..30f,
                steps = 28,
                onChange = viewModel::setDefaultUncertaintyDeg,
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
private fun SliderRow(
    title: String,
    subtitle: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
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
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}
