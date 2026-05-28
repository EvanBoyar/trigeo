package com.trigeo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trigeo.app.data.OfflineRegionInfo
import com.trigeo.app.data.OfflineRegionsRepository
import com.trigeo.app.data.SettingsRepository
import com.trigeo.app.domain.Defaults
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val offlineRegionsRepo: OfflineRegionsRepository,
) : ViewModel() {

    val defaultBidirectional: StateFlow<Boolean> = repo.defaultBidirectional
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val defaultUncertaintyDeg: StateFlow<Float> = repo.defaultUncertaintyDeg
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Defaults.UNCERTAINTY_DEG.toFloat(),
        )

    fun setDefaultBidirectional(value: Boolean) {
        viewModelScope.launch { repo.setDefaultBidirectional(value) }
    }

    fun setDefaultUncertaintyDeg(value: Float) {
        viewModelScope.launch { repo.setDefaultUncertaintyDeg(value) }
    }

    val tipButtonEnabled: StateFlow<Boolean> = repo.tipButtonEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setTipButtonEnabled(value: Boolean) {
        viewModelScope.launch { repo.setTipButtonEnabled(value) }
    }

    val minFixRangeMeters: StateFlow<Float> = repo.minFixRangeMeters
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Defaults.MIN_FIX_RANGE_METERS.toFloat(),
        )

    fun setMinFixRangeMeters(value: Float) {
        viewModelScope.launch { repo.setMinFixRangeMeters(value) }
    }

    val offlineRegions: StateFlow<List<OfflineRegionInfo>> = offlineRegionsRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteRegion(regionId: Long) {
        viewModelScope.launch { offlineRegionsRepo.delete(regionId) }
    }

    companion object {
        fun factory(
            repo: SettingsRepository,
            offlineRegionsRepo: OfflineRegionsRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(repo, offlineRegionsRepo) as T
        }
    }
}
