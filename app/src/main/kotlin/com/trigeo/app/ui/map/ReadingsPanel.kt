package com.trigeo.app.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.trigeo.app.domain.Reading
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingsPanel(
    readings: List<Reading>,
    onToggleVisible: (UUID, Boolean) -> Unit,
    onEdit: (Reading) -> Unit,
    onDelete: (Reading) -> Unit,
    onRestore: (Reading) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pendingDeleteJobs = remember { mutableStateListOf<Job>() }
    val dismissAllPending = {
        pendingDeleteJobs.toList().forEach { it.cancel() }
        pendingDeleteJobs.clear()
    }
    val bottomReserve by animateDpAsState(
        targetValue = if (snackbarHostState.currentSnackbarData != null) 72.dp else 0.dp,
        label = "snackbar-bottom-reserve",
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = bottomReserve),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = readings.asReversed(), key = { it.id }) { reading ->
                            SwipeRevealReadingRow(
                                reading = reading,
                                onToggle = { onToggleVisible(reading.id, !reading.visible) },
                                onEdit = { onEdit(reading) },
                                onDelete = {
                                    onDelete(reading)
                                    val job = scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Reading deleted",
                                            actionLabel = "Undo",
                                            withDismissAction = false,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onRestore(reading)
                                        }
                                    }
                                    pendingDeleteJobs.add(job)
                                    job.invokeOnCompletion { pendingDeleteJobs.remove(job) }
                                },
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { data ->
                Snackbar(
                    action = data.visuals.actionLabel?.let { label ->
                        {
                            TextButton(
                                onClick = { data.performAction() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = SnackbarDefaults.actionContentColor,
                                ),
                            ) { Text(label) }
                        }
                    },
                    dismissAction = {
                        IconButton(onClick = { dismissAllPending() }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Dismiss all",
                                tint = SnackbarDefaults.dismissActionContentColor,
                            )
                        }
                    },
                ) {
                    Text(data.visuals.message)
                }
            }
        }
    }
}

@Composable
private fun SwipeRevealReadingRow(
    reading: Reading,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { 104.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            FilledTonalButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Delete")
            }
        }
        ReadingRow(
            reading = reading,
            onToggle = onToggle,
            onEdit = {
                if (offset.value > 0f) {
                    scope.launch { offset.animateTo(0f) }
                } else {
                    onEdit()
                }
            },
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = if (offset.value > revealPx / 2f) revealPx else 0f
                                offset.animateTo(target)
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            scope.launch {
                                offset.snapTo(
                                    (offset.value + delta).coerceIn(0f, revealPx * 1.15f),
                                )
                            }
                        },
                    )
                },
        )
    }
}

@Composable
private fun ReadingRow(
    reading: Reading,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
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
