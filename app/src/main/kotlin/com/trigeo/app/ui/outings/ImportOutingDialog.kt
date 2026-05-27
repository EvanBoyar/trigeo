package com.trigeo.app.ui.outings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.trigeo.app.domain.Outing
import com.trigeo.app.io.OutingShareCodec
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportOutingDialog(
    existingOutings: List<Outing>,
    onImport: (text: String, targetOutingId: UUID?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialClipboardText(context)) }
    val preview = remember(text) { OutingShareCodec.decode(text).getOrNull() }
    val canImport = preview != null

    var mode by remember { mutableStateOf(ImportMode.CREATE_NEW) }
    var mergeTarget by remember { mutableStateOf<Outing?>(existingOutings.firstOrNull()) }
    var dropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (text.isBlank()) text = initialClipboardText(context)
    }
    LaunchedEffect(preview) {
        if (preview != null && preview.outingId != null) {
            val matching = existingOutings.firstOrNull { it.id == preview.outingId }
            if (matching != null) {
                mode = ImportMode.MERGE
                mergeTarget = matching
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a Trigeo share link (starts with trigeo:v1:). The text can include surrounding chat content.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Share text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 180.dp),
                )
                when {
                    text.isBlank() -> {}
                    preview != null -> {
                        Text(
                            "Contains \"${preview.outingName ?: "Untitled outing"}\" with ${preview.readings.size} reading${if (preview.readings.size == 1) "" else "s"}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("Import into:", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = mode == ImportMode.CREATE_NEW,
                                onClick = { mode = ImportMode.CREATE_NEW },
                            )
                            Text("Create new outing", modifier = Modifier.padding(start = 4.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = mode == ImportMode.MERGE,
                                enabled = existingOutings.isNotEmpty(),
                                onClick = { mode = ImportMode.MERGE },
                            )
                            Text(
                                "Merge into existing",
                                color = if (existingOutings.isEmpty())
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        if (mode == ImportMode.MERGE && existingOutings.isNotEmpty()) {
                            ExposedDropdownMenuBox(
                                expanded = dropdownOpen,
                                onExpandedChange = { dropdownOpen = it },
                            ) {
                                OutlinedTextField(
                                    value = mergeTarget?.displayName ?: "Pick an outing",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Target outing") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth(),
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownOpen,
                                    onDismissRequest = { dropdownOpen = false },
                                ) {
                                    existingOutings.forEach { outing ->
                                        DropdownMenuItem(
                                            text = { Text(outing.displayName) },
                                            onClick = {
                                                mergeTarget = outing
                                                dropdownOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val err = OutingShareCodec.decode(text).exceptionOrNull()
                        Text(
                            err?.message ?: "Couldn't read that as a Trigeo share.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            val target = if (mode == ImportMode.MERGE) mergeTarget?.id else null
            val enabled = canImport && (mode == ImportMode.CREATE_NEW || target != null)
            TextButton(enabled = enabled, onClick = { onImport(text, target) }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private enum class ImportMode { CREATE_NEW, MERGE }

private fun initialClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = clipboard.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    val item = clip.getItemAt(0)
    val text = item?.coerceToText(context)?.toString().orEmpty()
    return if (text.contains("trigeo:v1:")) text else ""
}
