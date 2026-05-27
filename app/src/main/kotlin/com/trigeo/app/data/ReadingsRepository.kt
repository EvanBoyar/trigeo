package com.trigeo.app.data

import com.trigeo.app.domain.BearingCapture
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Reading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

class ReadingsRepository(
    private val dao: ReadingDao,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> UUID = UUID::randomUUID,
) {
    fun observeByOuting(outingId: UUID): Flow<List<Reading>> =
        dao.observeByOuting(outingId.toString()).map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: UUID): Reading? = dao.findById(id.toString())?.toDomain()

    suspend fun create(
        outingId: UUID,
        point: GeoPoint,
        bearing: BearingCapture,
        bidirectional: Boolean,
        name: String? = null,
    ): Reading {
        val reading = Reading(
            id = idFactory(),
            outingId = outingId,
            name = name?.takeIf { it.isNotBlank() },
            point = point,
            bearing = bearing,
            bidirectional = bidirectional,
            visible = true,
            createdAt = clock.instant(),
        )
        dao.upsert(ReadingEntity.fromDomain(reading))
        return reading
    }

    suspend fun update(reading: Reading) {
        dao.upsert(ReadingEntity.fromDomain(reading))
    }

    suspend fun insertImported(
        outingId: UUID,
        readingId: UUID?,
        name: String?,
        point: GeoPoint,
        bearing: BearingCapture,
        bidirectional: Boolean,
        createdAt: Instant,
    ): InsertOutcome {
        val id = readingId ?: idFactory()
        if (readingId != null && dao.existsInOuting(outingId.toString(), id.toString())) {
            return InsertOutcome.Skipped(id)
        }
        val reading = Reading(
            id = id,
            outingId = outingId,
            name = name?.takeIf { it.isNotBlank() },
            point = point,
            bearing = bearing,
            bidirectional = bidirectional,
            visible = true,
            createdAt = createdAt,
        )
        dao.upsert(ReadingEntity.fromDomain(reading))
        return InsertOutcome.Inserted(reading)
    }

    sealed class InsertOutcome {
        data class Inserted(val reading: Reading) : InsertOutcome()
        data class Skipped(val readingId: UUID) : InsertOutcome()
    }

    suspend fun setVisible(id: UUID, visible: Boolean) {
        dao.setVisible(id.toString(), visible)
    }

    suspend fun rename(id: UUID, newName: String?) {
        dao.rename(id.toString(), newName?.takeIf { it.isNotBlank() })
    }

    suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }
}
