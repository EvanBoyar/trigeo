package com.trigeo.app.ui.outings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trigeo.app.data.OutingsRepository
import com.trigeo.app.domain.Outing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class OutingsViewModel(
    private val repo: OutingsRepository,
) : ViewModel() {

    val outings: StateFlow<List<Outing>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String?, onCreated: (Outing) -> Unit = {}) {
        viewModelScope.launch {
            val outing = repo.create(name)
            onCreated(outing)
        }
    }

    fun rename(id: UUID, newName: String?) {
        viewModelScope.launch { repo.rename(id, newName) }
    }

    fun delete(id: UUID) {
        viewModelScope.launch { repo.delete(id) }
    }

    companion object {
        fun factory(repo: OutingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OutingsViewModel(repo) as T
            }
    }
}
