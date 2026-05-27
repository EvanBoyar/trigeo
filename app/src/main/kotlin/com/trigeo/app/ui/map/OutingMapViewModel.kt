package com.trigeo.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trigeo.app.data.OutingsRepository
import com.trigeo.app.domain.Outing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class OutingMapViewModel(
    private val repo: OutingsRepository,
    private val outingId: UUID,
) : ViewModel() {

    private val _outing = MutableStateFlow<Outing?>(null)
    val outing: StateFlow<Outing?> = _outing.asStateFlow()

    init {
        viewModelScope.launch {
            _outing.value = repo.get(outingId)
        }
    }

    companion object {
        fun factory(repo: OutingsRepository, outingId: UUID): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OutingMapViewModel(repo, outingId) as T
            }
    }
}
