package com.trigeo.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Reading
import com.trigeo.app.domain.ReadingDirection
import com.trigeo.app.data.RegionProgress
import com.trigeo.app.geo.Triangulation
import com.trigeo.app.map.CameraRequest
import com.trigeo.app.map.MapBoundsHolder
import com.trigeo.app.map.MapTileStyle
import com.trigeo.app.BuildConfig
import com.trigeo.app.map.OutingMap
import com.trigeo.app.sensors.CompassAccuracy
import com.trigeo.app.sensors.DebugCompassOverride
import com.trigeo.app.ui.permissions.rememberLocationPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutingMapScreen(
    viewModel: OutingMapViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val outing by viewModel.outing.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val liveLocation by viewModel.liveLocation.collectAsState()
    val liveCompass by viewModel.liveCompass.collectAsState()
    val compassAvailable = viewModel.compassAvailable
    val defaultDirection by viewModel.defaultDirection.collectAsState()
    val defaultUncertaintyDeg by viewModel.defaultUncertaintyDeg.collectAsState()
    val minFixRangeMeters by viewModel.minFixRangeMeters.collectAsState()
    val tileStyle by viewModel.tileStyle.collectAsState()

    val permission = rememberLocationPermission()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(permission.granted) {
        if (permission.granted) viewModel.startSensors()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startSensors()
                Lifecycle.Event.ON_STOP -> viewModel.stopSensors()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var cameraRequest by remember { mutableStateOf<CameraRequest?>(null) }
    var hasAutoCentered by remember { mutableStateOf(false) }
    LaunchedEffect(liveLocation) {
        if (!hasAutoCentered) {
            liveLocation?.let {
                cameraRequest = CameraRequest(GeoPoint(it.latitude, it.longitude))
                hasAutoCentered = true
            }
        }
    }

    var lockToCompass by remember { mutableStateOf(false) }
    val mapBearing = if (lockToCompass) (liveCompass?.trueDeg ?: 0.0) else 0.0

    var showCapture by remember { mutableStateOf(false) }
    var longPressPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var editTarget by remember { mutableStateOf<Reading?>(null) }
    var showPanel by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showFix by remember { mutableStateOf(true) }
    var readingsOnMap by remember { mutableStateOf(true) }
    val panelOpen = showCapture || editTarget != null

    val boundsHolder = remember { MapBoundsHolder() }
    var downloadBounds by remember { mutableStateOf<org.maplibre.android.geometry.LatLngBounds?>(null) }
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    val mapReadings = if (readingsOnMap) readings else emptyList()
    val visibleReadings = remember(mapReadings) { mapReadings.filter { it.visible } }
    val fix = remember(visibleReadings, showFix, minFixRangeMeters) {
        if (showFix) Triangulation.solve(visibleReadings, minFixRangeMeters.toDouble()) else null
    }

    val quickBlockedReason: String? = when {
        !compassAvailable -> "No compass on this device"
        liveLocation == null -> "Waiting for GPS fix"
        liveCompass == null -> "Waiting for compass"
        else -> null
    }
    val quickReady = quickBlockedReason == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(outing?.displayName ?: "Outing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!panelOpen) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            liveLocation?.let {
                                cameraRequest = CameraRequest(GeoPoint(it.latitude, it.longitude))
                            }
                        },
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Recenter on me")
                    }
                    SmallFloatingActionButton(
                        onClick = { lockToCompass = !lockToCompass },
                        containerColor = if (lockToCompass)
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        else
                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (lockToCompass)
                            androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                        else
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    ) {
                        Icon(
                            Icons.Filled.Explore,
                            contentDescription = if (lockToCompass)
                                "Lock to compass (on)" else "Lock to compass (off)",
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = { showFix = !showFix },
                        containerColor = if (showFix)
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        else
                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (showFix)
                            androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                        else
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Map,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = if (showFix) "Hide fix" else "Show fix",
                                tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    SmallFloatingActionButton(onClick = { showLayers = true }) {
                        Icon(Icons.Filled.Layers, contentDescription = "Map style")
                    }
                    SmallFloatingActionButton(onClick = { showPanel = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Readings")
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            if (!permission.granted) permission.request()
                            showCapture = true
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add reading with options")
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (!permission.granted) {
                                permission.request()
                                return@ExtendedFloatingActionButton
                            }
                            if (!quickReady) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(quickBlockedReason!!)
                                }
                                return@ExtendedFloatingActionButton
                            }
                            viewModel.quickCreateReading { created ->
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Reading added",
                                        actionLabel = "Undo",
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.deleteReading(created.id)
                                    }
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
                        text = { Text("Quick add") },
                        containerColor = if (quickReady) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (quickReady) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                OutingMap(
                    readings = mapReadings,
                    cameraRequest = cameraRequest,
                    liveLocation = liveLocation?.let { GeoPoint(it.latitude, it.longitude) },
                    liveAccuracyMeters = liveLocation?.takeIf { it.hasAccuracy() }?.accuracy,
                    liveBearingDeg = liveCompass?.trueDeg,
                    liveUncertaintyDeg = defaultUncertaintyDeg.toDouble(),
                    liveDirection = defaultDirection,
                    tileStyle = tileStyle,
                    fix = fix,
                    pendingPoint = if (showCapture) longPressPoint else null,
                    bearingDeg = mapBearing,
                    rotationEnabled = !lockToCompass,
                    boundsHolder = boundsHolder,
                    onLongPress = { point ->
                        if (!panelOpen) {
                            longPressPoint = point
                            showCapture = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                CenterlineTick(
                    pointingDown = true,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp),
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 24.dp)
                        .widthIn(max = 200.dp),
                ) { data ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = 2.dp,
                                top = 2.dp,
                                bottom = 2.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                data.visuals.message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            data.visuals.actionLabel?.let { label ->
                                TextButton(
                                    onClick = { data.performAction() },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 0.dp,
                                    ),
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            IconButton(
                                onClick = { data.dismiss() },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                CenterlineTick(
                    pointingDown = false,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!compassAvailable) {
                        CompassWarningCard(
                            headline = "No compass detected",
                            detail = "This device has no compass. Add readings by entering the bearing manually.",
                        )
                    } else {
                        val effectiveAccuracy =
                            (if (BuildConfig.DEBUG) DebugCompassOverride.forcedAccuracy else null)
                                ?: liveCompass?.accuracy
                        effectiveAccuracy?.takeIf { it != CompassAccuracy.HIGH }?.let { acc ->
                            CompassWarningCard(
                                headline = "Compass calibration ${acc.calibrationLabel()}",
                                detail = "Bearings may be off. Move the phone in a figure 8 to recalibrate.",
                            )
                        }
                    }
                    fix?.let { f ->
                        FixCoordinatesCard(fix = f)
                    }
                }
            }
            if (showCapture) {
                ReadingPanel(
                    title = if (longPressPoint != null) "Add reading at point" else "Add reading",
                    initial = ReadingDraft(
                        direction = defaultDirection,
                        uncertaintyDeg = defaultUncertaintyDeg,
                        useGps = longPressPoint == null,
                        manualLat = longPressPoint?.latitude?.let { "%.6f".format(it) }.orEmpty(),
                        manualLon = longPressPoint?.longitude?.let { "%.6f".format(it) }.orEmpty(),
                    ),
                    locationPermissionGranted = permission.granted,
                    onRequestPermission = permission.request,
                    liveLocation = liveLocation,
                    liveCompass = liveCompass,
                    onSave = { values ->
                        viewModel.createReading(
                            point = values.point!!,
                            bearing = values.bearing!!,
                            startBearingDeg = values.startBearingDeg,
                            stopBearingDeg = values.stopBearingDeg,
                            direction = values.direction,
                            name = values.name,
                        )
                        showCapture = false
                        longPressPoint = null
                    },
                    onDismiss = {
                        showCapture = false
                        longPressPoint = null
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else editTarget?.let { reading ->
                val hasStartStop = reading.startBearingDeg != null && reading.stopBearingDeg != null
                ReadingPanel(
                    title = "Edit reading",
                    capturedAtUtc = reading.createdAtUtc,
                    initial = ReadingDraft(
                        name = reading.name.orEmpty(),
                        useGps = false,
                        manualLat = "%.6f".format(reading.point.latitude),
                        manualLon = "%.6f".format(reading.point.longitude),
                        bearingMode = if (hasStartStop) BearingMode.START_STOP else BearingMode.CUSTOM,
                        manualBearingDeg = "%.1f".format(reading.bearing.centerDeg),
                        startBearingText = reading.startBearingDeg?.let { "%.1f".format(it) }.orEmpty(),
                        stopBearingText = reading.stopBearingDeg?.let { "%.1f".format(it) }.orEmpty(),
                        uncertaintyDeg = reading.bearing.uncertaintyDeg.toFloat(),
                        direction = reading.direction,
                    ),
                    locationPermissionGranted = permission.granted,
                    onRequestPermission = permission.request,
                    liveLocation = liveLocation,
                    liveCompass = liveCompass,
                    onSave = { values ->
                        viewModel.updateReading(
                            reading.copy(
                                point = values.point!!,
                                bearing = values.bearing!!,
                                startBearingDeg = values.startBearingDeg,
                                stopBearingDeg = values.stopBearingDeg,
                                direction = values.direction,
                                name = values.name,
                            ),
                        )
                        editTarget = null
                    },
                    onDelete = {
                        viewModel.deleteReading(reading.id)
                        editTarget = null
                    },
                    onShare = {
                        viewModel.shareReadingText(reading) { text ->
                            val title = reading.displayName
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Trigeo reading: $title")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Trigeo reading \"$title\". Paste this into Trigeo to import:\n\n$text",
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "Share reading"))
                        }
                    },
                    onDismiss = { editTarget = null },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }

    if (showPanel) {
        ReadingsPanel(
            readings = readings,
            readingsOnMap = readingsOnMap,
            onToggleReadingsOnMap = { readingsOnMap = !readingsOnMap },
            onToggleVisible = { id, visible -> viewModel.setVisible(id, visible) },
            onEdit = { reading ->
                showPanel = false
                editTarget = reading
            },
            onDelete = { reading -> viewModel.deleteReading(reading.id) },
            onRestore = { reading -> viewModel.updateReading(reading) },
            onDismiss = { showPanel = false },
        )
    }

    if (showLayers) {
        LayersSheet(
            current = tileStyle,
            onSelect = { style ->
                viewModel.setTileStyle(style)
                showLayers = false
            },
            onDownloadArea = {
                showLayers = false
                viewModel.clearDownloadProgress()
                downloadBounds = boundsHolder.visibleBounds()
            },
            onDismiss = { showLayers = false },
        )
    }

    downloadBounds?.let { b ->
        DownloadRegionDialog(
            bounds = b,
            tileStyle = tileStyle,
            progress = downloadProgress,
            onConfirm = { name, minZoom, maxZoom ->
                viewModel.startDownload(name, tileStyle, b, minZoom, maxZoom)
            },
            onCancel = { viewModel.cancelDownload() },
            onDismiss = {
                downloadBounds = null
                viewModel.clearDownloadProgress()
            },
        )
    }
}

@Composable
private fun CenterlineTick(
    pointingDown: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 20.dp, height = 13.dp)) {
        val path = Path().apply {
            if (pointingDown) {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            } else {
                moveTo(0f, size.height)
                lineTo(size.width, size.height)
                lineTo(size.width / 2f, 0f)
                close()
            }
        }
        drawPath(path, Color(0xFFEF4444))
        drawPath(path, Color.White, style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
private fun CompassWarningCard(
    headline: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(headline, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun CompassAccuracy.calibrationLabel(): String = when (this) {
    CompassAccuracy.HIGH -> "high"
    CompassAccuracy.MEDIUM -> "medium"
    CompassAccuracy.LOW -> "low"
    CompassAccuracy.UNRELIABLE -> "unreliable"
    CompassAccuracy.NO_CONTACT -> "unavailable"
    CompassAccuracy.UNKNOWN -> "unknown"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(
    current: MapTileStyle,
    onSelect: (MapTileStyle) -> Unit,
    onDownloadArea: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Map style", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            MapTileStyle.entries.forEach { style ->
                StyleRow(
                    style = style,
                    selected = style == current,
                    onClick = { onSelect(style) },
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDownloadArea)
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download area for offline", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Cache the tiles currently in view at chosen zoom levels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StyleRow(
    style: MapTileStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(style.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                style.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
