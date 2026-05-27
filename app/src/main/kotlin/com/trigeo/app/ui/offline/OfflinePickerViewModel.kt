package com.trigeo.app.ui.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trigeo.app.data.OfflineRegionsRepository
import com.trigeo.app.data.RegionProgress
import com.trigeo.app.data.SettingsRepository
import com.trigeo.app.map.MapTileStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.maplibre.android.geometry.LatLngBounds

class OfflinePickerViewModel(
    settingsRepo: SettingsRepository,
    private val offlineRegionsRepo: OfflineRegionsRepository,
) : ViewModel() {

    val tileStyle: StateFlow<MapTileStyle> = settingsRepo.tileStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapTileStyle.OSM)

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
            settingsRepo: SettingsRepository,
            offlineRegionsRepo: OfflineRegionsRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OfflinePickerViewModel(settingsRepo, offlineRegionsRepo) as T
        }
    }
}
