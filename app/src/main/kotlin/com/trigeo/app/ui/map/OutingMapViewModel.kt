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
import com.trigeo.app.BuildConfig
import com.trigeo.app.sensors.CompassReading
import com.trigeo.app.sensors.CompassService
import com.trigeo.app.sensors.DebugCompassOverride
import com.trigeo.app.sensors.LocationService
import com.trigeo.app.sensors.RestartableCollector
import org.maplibre.android.geometry.LatLngBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class PendingQuickStart(
    val point: GeoPoint,
    val startBearingDeg: Double,
)

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

    val defaultDirection: StateFlow<ReadingDirection> = settingsRepo.defaultDirection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingDirection.NORMAL)

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

    val defaultStartStopMode: StateFlow<Boolean> = settingsRepo.defaultStartStopMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _pendingQuickStart = MutableStateFlow<PendingQuickStart?>(null)
    val pendingQuickStart: StateFlow<PendingQuickStart?> = _pendingQuickStart.asStateFlow()

    fun setTileStyle(value: MapTileStyle) {
        viewModelScope.launch { settingsRepo.setTileStyle(value) }
    }

    private val locationCollector = RestartableCollector<Location>(viewModelScope) {
        if (locationService.hasPermission()) locationService.locationUpdates() else null
    }
    private val compassCollector = RestartableCollector<CompassReading>(viewModelScope) {
        if (compassService.isAvailable()) {
            compassService.headingUpdates(
                latitudeProvider = { locationCollector.latest.value?.latitude ?: 0.0 },
                longitudeProvider = { locationCollector.latest.value?.longitude ?: 0.0 },
                altitudeMetersProvider = { locationCollector.latest.value?.altitude ?: 0.0 },
            )
        } else {
            null
        }
    }

    val liveLocation: StateFlow<Location?> = locationCollector.latest
    val liveCompass: StateFlow<CompassReading?> = compassCollector.latest

    val compassAvailable: Boolean =
        if (BuildConfig.DEBUG && DebugCompassOverride.forceNoCompass) false
        else compassService.isAvailable()

    init {
        viewModelScope.launch { _outing.value = outingsRepo.get(outingId) }
    }

    fun startSensors() {
        locationCollector.start()
        compassCollector.start()
    }

    fun stopSensors() {
        locationCollector.stop()
        compassCollector.stop()
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

    fun quickCreateReading(onCreated: (Reading) -> Unit) {
        val loc = liveLocation.value ?: return
        val compass = liveCompass.value ?: return
        val uncertainty = defaultUncertaintyDeg.value.toDouble()
        val direction = defaultDirection.value
        val point = GeoPoint(loc.latitude, loc.longitude)
        val bearing = BearingCapture.fromCenter(compass.trueDeg, uncertainty)
        viewModelScope.launch {
            val created = readingsRepo.create(
                outingId = outingId,
                point = point,
                bearing = bearing,
                startBearingDeg = null,
                stopBearingDeg = null,
                direction = direction,
                name = null,
            )
            onCreated(created)
        }
    }

    fun startQuickCapture() {
        val loc = liveLocation.value ?: return
        val compass = liveCompass.value ?: return
        _pendingQuickStart.value = PendingQuickStart(
            point = GeoPoint(loc.latitude, loc.longitude),
            startBearingDeg = compass.trueDeg,
        )
    }

    fun completeQuickCapture(onCreated: (Reading) -> Unit) {
        val pending = _pendingQuickStart.value ?: return
        val compass = liveCompass.value ?: return
        val bearing = BearingCapture.fromStartStop(pending.startBearingDeg, compass.trueDeg)
        val direction = defaultDirection.value
        _pendingQuickStart.value = null
        viewModelScope.launch {
            val created = readingsRepo.create(
                outingId = outingId,
                point = pending.point,
                bearing = bearing,
                startBearingDeg = pending.startBearingDeg,
                stopBearingDeg = compass.trueDeg,
                direction = direction,
                name = null,
            )
            onCreated(created)
        }
    }

    fun cancelQuickCapture() {
        _pendingQuickStart.value = null
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
