package com.trigeo.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trigeo.app.domain.BearingCapture
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Reading
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "readings",
    foreignKeys = [
        ForeignKey(
            entity = OutingEntity::class,
            parentColumns = ["id"],
            childColumns = ["outingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("outingId")],
)
data class ReadingEntity(
    @PrimaryKey val id: String,
    val outingId: String,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val bearingCenterDeg: Double,
    val bearingHalfWidthDeg: Double,
    val startBearingDeg: Double?,
    val stopBearingDeg: Double?,
    val bidirectional: Boolean,
    val visible: Boolean,
    val createdAtEpochMs: Long,
) {
    fun toDomain(): Reading = Reading(
        id = UUID.fromString(id),
        outingId = UUID.fromString(outingId),
        name = name,
        point = GeoPoint(latitude, longitude),
        bearing = BearingCapture(bearingCenterDeg, bearingHalfWidthDeg),
        startBearingDeg = startBearingDeg,
        stopBearingDeg = stopBearingDeg,
        bidirectional = bidirectional,
        visible = visible,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    )

    companion object {
        fun fromDomain(reading: Reading): ReadingEntity = ReadingEntity(
            id = reading.id.toString(),
            outingId = reading.outingId.toString(),
            name = reading.name,
            latitude = reading.point.latitude,
            longitude = reading.point.longitude,
            bearingCenterDeg = reading.bearing.centerDeg,
            bearingHalfWidthDeg = reading.bearing.halfWidthDeg,
            startBearingDeg = reading.startBearingDeg,
            stopBearingDeg = reading.stopBearingDeg,
            bidirectional = reading.bidirectional,
            visible = reading.visible,
            createdAtEpochMs = reading.createdAt.toEpochMilli(),
        )
    }
}
