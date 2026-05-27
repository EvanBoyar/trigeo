package com.trigeo.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Reading
import com.trigeo.app.map.OutingMap
import com.trigeo.app.ui.permissions.rememberLocationPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutingMapScreen(
    viewModel: OutingMapViewModel,
    onBack: () -> Unit,
) {
    val outing by viewModel.outing.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val liveLocation by viewModel.liveLocation.collectAsState()
    val liveCompass by viewModel.liveCompass.collectAsState()

    val permission = rememberLocationPermission()

    LaunchedEffect(permission.granted) {
        if (permission.granted) viewModel.startSensors()
    }

    var cameraTarget by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(liveLocation) {
        if (cameraTarget == null) {
            liveLocation?.let { cameraTarget = GeoPoint(it.latitude, it.longitude) }
        }
    }

    var showCapture by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Reading?>(null) }
    var showPanel by remember { mutableStateOf(false) }

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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(onClick = { showPanel = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Readings")
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!permission.granted) permission.request()
                        showCapture = true
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add reading") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutingMap(
                readings = readings,
                cameraTarget = cameraTarget,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showCapture) {
        ReadingSheet(
            title = "Add reading",
            initial = ReadingDraft(),
            locationPermissionGranted = permission.granted,
            onRequestPermission = permission.request,
            liveLocation = liveLocation,
            liveCompass = liveCompass,
            onSave = { point, bearing, name ->
                viewModel.createReading(point, bearing, name)
                showCapture = false
            },
            onDismiss = { showCapture = false },
        )
    }

    editTarget?.let { reading ->
        ReadingSheet(
            title = "Edit reading",
            initial = ReadingDraft(
                name = reading.name.orEmpty(),
                useGps = false,
                manualLat = "%.6f".format(reading.point.latitude),
                manualLon = "%.6f".format(reading.point.longitude),
                useCompass = false,
                manualBearingDeg = "%.1f".format(reading.bearing.centerDeg),
                uncertaintyDeg = reading.bearing.uncertaintyDeg.toFloat(),
            ),
            locationPermissionGranted = permission.granted,
            onRequestPermission = permission.request,
            liveLocation = liveLocation,
            liveCompass = liveCompass,
            onSave = { point, bearing, name ->
                viewModel.updateReading(
                    reading.copy(point = point, bearing = bearing, name = name),
                )
                editTarget = null
            },
            onDelete = {
                viewModel.deleteReading(reading.id)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    if (showPanel) {
        ReadingsPanel(
            readings = readings,
            onToggleVisible = { id, visible -> viewModel.setVisible(id, visible) },
            onEdit = { reading ->
                showPanel = false
                editTarget = reading
            },
            onDismiss = { showPanel = false },
        )
    }
}
