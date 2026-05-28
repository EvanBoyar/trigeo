package com.trigeo.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.trigeo.app.domain.Reading
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingsPanel(
    readings: List<Reading>,
    onToggleVisible: (UUID, Boolean) -> Unit,
    onEdit: (Reading) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Readings", style = MaterialTheme.typography.headlineSmall)
            if (readings.isEmpty()) {
                Text(
                    "No readings yet. Use the Add reading button on the map.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items = readings, key = { it.id }) { reading ->
                        ReadingRow(
                            reading = reading,
                            onToggle = { onToggleVisible(reading.id, !reading.visible) },
                            onEdit = { onEdit(reading) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingRow(
    reading: Reading,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle) {
                if (reading.visible) {
                    Icon(Icons.Filled.Visibility, contentDescription = "Hide")
                } else {
                    Icon(
                        Icons.Filled.VisibilityOff,
                        contentDescription = "Show",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reading.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (reading.visible) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    reading.createdAtUtc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "%.5f, %.5f".format(reading.point.latitude, reading.point.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                val directionLabel = when (reading.direction) {
                    com.trigeo.app.domain.ReadingDirection.NORMAL -> null
                    com.trigeo.app.domain.ReadingDirection.BIDIRECTIONAL -> "both ways"
                    com.trigeo.app.domain.ReadingDirection.REVERSED -> "reversed"
                }
                Text(
                    buildString {
                        append("%.1f° ± %.1f°".format(reading.bearing.centerDeg, reading.bearing.halfWidthDeg))
                        if (directionLabel != null) append("  •  $directionLabel")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
