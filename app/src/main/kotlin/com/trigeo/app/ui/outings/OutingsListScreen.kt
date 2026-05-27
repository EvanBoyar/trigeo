package com.trigeo.app.ui.outings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trigeo.app.R
import com.trigeo.app.domain.Outing
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutingsListScreen(
    viewModel: OutingsViewModel,
    onOpen: (Outing) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val outings by viewModel.outings.collectAsState()
    val tipEnabled by viewModel.tipButtonEnabled.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var manageTarget by remember { mutableStateOf<Outing?>(null) }
    var renameTarget by remember { mutableStateOf<Outing?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Trigeo") },
                navigationIcon = {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Trigeo",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp),
                    )
                },
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Import outing")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New outing") },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (outings.isEmpty()) {
                EmptyState(padding)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = outings, key = { it.id }) { outing ->
                        OutingRow(
                            outing = outing,
                            onClick = { onOpen(outing) },
                            onLongPress = { manageTarget = outing },
                        )
                    }
                }
            }
            if (tipEnabled) {
                SmallFloatingActionButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/elbow"))
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = 16.dp,
                            bottom = padding.calculateBottomPadding() + 16.dp,
                        ),
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Tip")
                }
            }
        }
    }

    if (showCreate) {
        OutingNameDialog(
            title = "New outing",
            confirmLabel = "Create",
            initial = "",
            onConfirm = { name ->
                showCreate = false
                viewModel.create(name) { created -> onOpen(created) }
            },
            onDismiss = { showCreate = false },
        )
    }

    renameTarget?.let { target ->
        OutingNameDialog(
            title = "Rename outing",
            confirmLabel = "Save",
            initial = target.name.orEmpty(),
            onConfirm = { name ->
                viewModel.rename(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    manageTarget?.let { target ->
        ManageOutingSheet(
            outing = target,
            onDismiss = { manageTarget = null },
            onRename = {
                manageTarget = null
                renameTarget = target
            },
            onShare = {
                manageTarget = null
                viewModel.shareText(target.id) { text ->
                    val title = target.displayName
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Trigeo outing: $title")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Trigeo outing \"$title\". Paste this into Trigeo to import:\n\n$text",
                        )
                    }
                    context.startActivity(Intent.createChooser(intent, "Share outing"))
                }
            },
            onDelete = {
                viewModel.delete(target.id)
                manageTarget = null
            },
        )
    }

    var importSummary by remember { mutableStateOf<OutingsViewModel.ImportSummary?>(null) }
    if (showImport) {
        ImportOutingDialog(
            existingOutings = outings,
            onImport = { text, target ->
                viewModel.import(text, target) { result ->
                    result.fold(
                        onSuccess = { summary ->
                            showImport = false
                            importSummary = summary
                        },
                        onFailure = { e ->
                            importError = e.message ?: "Couldn't import"
                        },
                    )
                }
            },
            onDismiss = { showImport = false },
        )
    }

    importError?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Import failed") },
            text = { Text(msg) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { importError = null }) { Text("OK") }
            },
        )
    }

    importSummary?.let { summary ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { importSummary = null },
            title = { Text("Imported") },
            text = {
                val parts = buildList {
                    add("${summary.inserted} reading${if (summary.inserted == 1) "" else "s"} added to \"${summary.outing.displayName}\"")
                    if (summary.skipped > 0) add("${summary.skipped} duplicate${if (summary.skipped == 1) "" else "s"} skipped")
                }
                Text(parts.joinToString(". ") + ".")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val outing = summary.outing
                    importSummary = null
                    onOpen(outing)
                }) { Text("Open") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { importSummary = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No outings yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap New outing to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutingRow(
    outing: Outing,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a").withZone(ZoneId.systemDefault())
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                outing.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatter.format(outing.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageOutingSheet(
    outing: Outing,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                outing.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            SheetAction(label = "Rename", onClick = onRename)
            SheetAction(label = "Share", onClick = onShare)
            SheetAction(label = "Delete", destructive = true, onClick = onDelete)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetAction(
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
