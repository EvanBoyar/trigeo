package com.trigeo.app.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.map.CameraRequest
import com.trigeo.app.map.MapBoundsHolder
import com.trigeo.app.map.OutingMap
import com.trigeo.app.ui.map.DownloadRegionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePickerScreen(
    viewModel: OfflinePickerViewModel,
    onBack: () -> Unit,
) {
    val tileStyle by viewModel.tileStyle.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()
    val boundsHolder = remember { MapBoundsHolder() }
    var pickedBounds by remember { mutableStateOf<org.maplibre.android.geometry.LatLngBounds?>(null) }
    var initialCameraSet by remember { mutableStateOf(false) }
    var cameraRequest by remember { mutableStateOf<CameraRequest?>(null) }

    // Open the map at world view by default; user pans to area of interest.
    if (!initialCameraSet) {
        cameraRequest = CameraRequest(GeoPoint(20.0, 0.0))
        initialCameraSet = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download offline area") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.clearDownloadProgress()
                    pickedBounds = boundsHolder.visibleBounds()
                },
                text = { Text("Use this area") },
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutingMap(
                readings = emptyList(),
                cameraRequest = cameraRequest,
                liveLocation = null,
                liveBearingDeg = null,
                liveUncertaintyDeg = 0.0,
                liveBidirectional = false,
                tileStyle = tileStyle,
                fix = null,
                pendingPoint = null,
                bearingDeg = 0.0,
                rotationEnabled = true,
                boundsHolder = boundsHolder,
                onLongPress = { },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    pickedBounds?.let { b ->
        DownloadRegionDialog(
            bounds = b,
            tileStyle = tileStyle,
            progress = progress,
            onConfirm = { name, minZoom, maxZoom ->
                viewModel.startDownload(name, tileStyle, b, minZoom, maxZoom)
            },
            onCancel = { viewModel.cancelDownload() },
            onDismiss = {
                pickedBounds = null
                viewModel.clearDownloadProgress()
            },
        )
    }
}
