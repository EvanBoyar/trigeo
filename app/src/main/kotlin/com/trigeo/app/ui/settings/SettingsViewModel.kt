package com.trigeo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trigeo.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
) : ViewModel() {

    val defaultBidirectional: StateFlow<Boolean> = repo.defaultBidirectional
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDefaultBidirectional(value: Boolean) {
        viewModelScope.launch { repo.setDefaultBidirectional(value) }
    }

    companion object {
        fun factory(repo: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(repo) as T
            }
    }
}
