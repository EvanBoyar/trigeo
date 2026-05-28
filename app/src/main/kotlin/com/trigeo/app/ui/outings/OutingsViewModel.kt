package com.trigeo.app.ui.outings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trigeo.app.data.OutingsRepository
import com.trigeo.app.data.ReadingsRepository
import com.trigeo.app.data.SettingsRepository
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
    settingsRepo: SettingsRepository,
) : ViewModel() {

    val outings: StateFlow<List<Outing>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tipButtonEnabled: StateFlow<Boolean> = settingsRepo.tipButtonEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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

    data class ImportSummary(val outing: Outing, val inserted: Int, val skipped: Int)

    fun import(
        text: String,
        targetOutingId: UUID?,
        onResult: (Result<ImportSummary>) -> Unit,
    ) {
        viewModelScope.launch {
            val share = OutingShareCodec.decode(text).getOrElse {
                onResult(Result.failure(it))
                return@launch
            }
            val outing = if (targetOutingId != null) {
                repo.get(targetOutingId) ?: run {
                    onResult(Result.failure(IllegalStateException("Target outing not found")))
                    return@launch
                }
            } else {
                repo.create(name = share.outingName, createdAt = share.outingCreatedAt)
            }
            var inserted = 0
            var skipped = 0
            share.readings.forEach { r ->
                val outcome = readingsRepo.insertImported(
                    outingId = outing.id,
                    readingId = r.id,
                    name = r.name,
                    point = r.point,
                    bearing = r.bearing,
                    startBearingDeg = r.startBearingDeg,
                    stopBearingDeg = r.stopBearingDeg,
                    direction = r.direction,
                    createdAt = r.createdAt,
                )
                when (outcome) {
                    is com.trigeo.app.data.ReadingsRepository.InsertOutcome.Inserted -> inserted++
                    is com.trigeo.app.data.ReadingsRepository.InsertOutcome.Skipped -> skipped++
                }
            }
            onResult(Result.success(ImportSummary(outing, inserted, skipped)))
        }
    }

    companion object {
        fun factory(
            repo: OutingsRepository,
            readingsRepo: ReadingsRepository,
            settingsRepo: SettingsRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OutingsViewModel(repo, readingsRepo, settingsRepo) as T
            }
    }
}
