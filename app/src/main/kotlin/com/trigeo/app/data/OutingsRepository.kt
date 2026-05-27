package com.trigeo.app.data

import com.trigeo.app.domain.Outing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

class OutingsRepository(
    private val dao: OutingDao,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> UUID = UUID::randomUUID,
) {
    fun observeAll(): Flow<List<Outing>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: UUID): Outing? = dao.findById(id.toString())?.toDomain()

    suspend fun create(name: String? = null, createdAt: Instant = clock.instant()): Outing {
        val outing = Outing(
            id = idFactory(),
            name = name?.takeIf { it.isNotBlank() },
            createdAt = createdAt,
        )
        dao.upsert(OutingEntity.fromDomain(outing))
        return outing
    }

    suspend fun rename(id: UUID, newName: String?) {
        dao.rename(id.toString(), newName?.takeIf { it.isNotBlank() })
    }

    suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }
}
