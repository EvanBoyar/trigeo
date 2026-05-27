package com.trigeo.app.ui.outings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trigeo.app.io.OutingShareCodec

@Composable
fun ImportOutingDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialClipboardText(context)) }
    val preview = remember(text) { OutingShareCodec.decode(text).getOrNull() }
    val canImport = preview != null

    LaunchedEffect(Unit) {
        if (text.isBlank()) text = initialClipboardText(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import outing") },
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
                        .heightIn(min = 120.dp, max = 220.dp),
                )
                when {
                    text.isBlank() -> {}
                    preview != null -> {
                        Text(
                            "Will import \"${preview.outingName ?: "Untitled outing"}\" with ${preview.readings.size} reading${if (preview.readings.size == 1) "" else "s"}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
            TextButton(enabled = canImport, onClick = { onImport(text) }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun initialClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = clipboard.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    val item = clip.getItemAt(0)
    val text = item?.coerceToText(context)?.toString().orEmpty()
    return if (text.contains("trigeo:v1:")) text else ""
}
