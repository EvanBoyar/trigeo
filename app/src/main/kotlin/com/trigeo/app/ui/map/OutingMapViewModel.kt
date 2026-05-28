package com.trigeo.app.ui.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trigeo.app.data.OfflineRegionsRepository
import com.trigeo.app.data.OutingsRepository
import com.trigeo.app.data.ReadingsRepository
import com.trigeo.app.data.RegionProgress
import com.trigeo.app.data.SettingsRepository
import com.trigeo.app.domain.BearingCapture
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Outing
import com.trigeo.app.domain.Reading
import com.trigeo.app.domain.ReadingDirection
import com.trigeo.app.io.OutingShareCodec
import com.trigeo.app.map.MapTileStyle
import com.trigeo.app.sensors.CompassReading
import com.trigeo.app.sensors.CompassService
import com.trigeo.app.sensors.LocationService
import kotlinx.coroutines.flow.collect
import org.maplibre.android.geometry.LatLngBounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class OutingMapViewModel(
    private val outingsRepo: OutingsRepository,
    private val readingsRepo: ReadingsRepository,
    private val settingsRepo: SettingsRepository,
    private val offlineRegionsRepo: OfflineRegionsRepository,
    private val locationService: LocationService,
    private val compassService: CompassService,
    private val outingId: UUID,
) : ViewModel() {

    private val _outing = MutableStateFlow<Outing?>(null)
    val outing: StateFlow<Outing?> = _outing.asStateFlow()

    val readings: StateFlow<List<Reading>> = readingsRepo.observeByOuting(outingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val defaultBidirectional: StateFlow<Boolean> = settingsRepo.defaultBidirectional
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val defaultUncertaintyDeg: StateFlow<Float> = settingsRepo.defaultUncertaintyDeg
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.trigeo.app.domain.Defaults.UNCERTAINTY_DEG.toFloat(),
        )

    val tileStyle: StateFlow<MapTileStyle> = settingsRepo.tileStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapTileStyle.OSM)

    val minFixRangeMeters: StateFlow<Float> = settingsRepo.minFixRangeMeters
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.trigeo.app.domain.Defaults.MIN_FIX_RANGE_METERS.toFloat(),
        )

    fun setTileStyle(value: MapTileStyle) {
        viewModelScope.launch { settingsRepo.setTileStyle(value) }
    }

    private val _liveLocation = MutableStateFlow<Location?>(null)
    val liveLocation: StateFlow<Location?> = _liveLocation.asStateFlow()

    private val _liveCompass = MutableStateFlow<CompassReading?>(null)
    val liveCompass: StateFlow<CompassReading?> = _liveCompass.asStateFlow()

    private var locationJob: Job? = null
    private var compassJob: Job? = null

    init {
        viewModelScope.launch { _outing.value = outingsRepo.get(outingId) }
    }

    fun startSensors() {
        if (locationJob == null && locationService.hasPermission()) {
            locationJob = viewModelScope.launch {
                locationService.locationUpdates().collect { _liveLocation.value = it }
            }
        }
        if (compassJob == null && compassService.isAvailable()) {
            compassJob = viewModelScope.launch {
                compassService.headingUpdates(
                    latitudeProvider = { _liveLocation.value?.latitude ?: 0.0 },
                    longitudeProvider = { _liveLocation.value?.longitude ?: 0.0 },
                    altitudeMetersProvider = { _liveLocation.value?.altitude ?: 0.0 },
                ).collect { _liveCompass.value = it }
            }
        }
    }

    fun stopSensors() {
        locationJob?.cancel(); locationJob = null
        compassJob?.cancel(); compassJob = null
    }

    override fun onCleared() {
        stopSensors()
        super.onCleared()
    }

    fun createReading(
        point: GeoPoint,
        bearing: BearingCapture,
        startBearingDeg: Double?,
        stopBearingDeg: Double?,
        direction: ReadingDirection,
        name: String?,
    ) {
        viewModelScope.launch {
            readingsRepo.create(
                outingId = outingId,
                point = point,
                bearing = bearing,
                startBearingDeg = startBearingDeg,
                stopBearingDeg = stopBearingDeg,
                direction = direction,
                name = name,
            )
        }
    }

    fun updateReading(reading: Reading) {
        viewModelScope.launch { readingsRepo.update(reading) }
    }

    fun deleteReading(id: UUID) {
        viewModelScope.launch { readingsRepo.delete(id) }
    }

    fun setVisible(id: UUID, visible: Boolean) {
        viewModelScope.launch { readingsRepo.setVisible(id, visible) }
    }

    fun shareReadingText(reading: Reading, onReady: (String) -> Unit) {
        val parent = outing.value ?: return
        onReady(OutingShareCodec.encodeReading(parent, reading))
    }

    val downloadProgress: StateFlow<RegionProgress?> = offlineRegionsRepo.activeProgress

    fun startDownload(
        name: String,
        tileStyle: MapTileStyle,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
    ) {
        offlineRegionsRepo.startDownload(name, tileStyle, bounds, minZoom, maxZoom)
    }

    fun cancelDownload() {
        offlineRegionsRepo.cancelActiveDownload()
    }

    fun clearDownloadProgress() {
        offlineRegionsRepo.clearActiveProgress()
    }

    companion object {
        fun factory(
            outingsRepo: OutingsRepository,
            readingsRepo: ReadingsRepository,
            settingsRepo: SettingsRepository,
            offlineRegionsRepo: OfflineRegionsRepository,
            locationService: LocationService,
            compassService: CompassService,
            outingId: UUID,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OutingMapViewModel(
                    outingsRepo, readingsRepo, settingsRepo, offlineRegionsRepo,
                    locationService, compassService, outingId,
                ) as T
        }
    }
}
