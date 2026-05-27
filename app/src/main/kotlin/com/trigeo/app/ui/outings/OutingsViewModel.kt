package com.trigeo.app.ui.outings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trigeo.app.data.OutingsRepository
import com.trigeo.app.data.ReadingsRepository
import com.trigeo.app.domain.Outing
import com.trigeo.app.io.OutingShareCodec
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class OutingsViewModel(
    private val repo: OutingsRepository,
    private val readingsRepo: ReadingsRepository,
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

    fun shareText(outingId: UUID, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val outing = repo.get(outingId) ?: return@launch
            val readings = readingsRepo.observeByOuting(outingId).first()
            onReady(OutingShareCodec.encode(outing, readings))
        }
    }

    fun import(text: String, onResult: (Result<Outing>) -> Unit) {
        viewModelScope.launch {
            val share = OutingShareCodec.decode(text).getOrElse {
                onResult(Result.failure(it))
                return@launch
            }
            val outing = repo.create(name = share.outingName, createdAt = share.outingCreatedAt)
            share.readings.forEach { r ->
                readingsRepo.insertImported(
                    outingId = outing.id,
                    name = r.name,
                    point = r.point,
                    bearing = r.bearing,
                    bidirectional = r.bidirectional,
                    createdAt = r.createdAt,
                )
            }
            onResult(Result.success(outing))
        }
    }

    companion object {
        fun factory(
            repo: OutingsRepository,
            readingsRepo: ReadingsRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OutingsViewModel(repo, readingsRepo) as T
            }
    }
}
