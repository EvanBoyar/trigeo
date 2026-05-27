package com.trigeo.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trigeo.app.domain.Outing
import java.time.Instant
import java.util.UUID

@Entity(tableName = "outings")
data class OutingEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val createdAtEpochMs: Long,
) {
    fun toDomain(): Outing = Outing(
        id = UUID.fromString(id),
        name = name,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    )

    companion object {
        fun fromDomain(outing: Outing): OutingEntity = OutingEntity(
            id = outing.id.toString(),
            name = outing.name,
            createdAtEpochMs = outing.createdAt.toEpochMilli(),
        )
    }
}
