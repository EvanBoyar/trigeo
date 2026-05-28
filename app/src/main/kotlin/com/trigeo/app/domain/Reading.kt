package com.trigeo.app.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val UTC_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

data class Reading(
    val id: UUID,
    val outingId: UUID,
    val name: String?,
    val point: GeoPoint,
    val bearing: BearingCapture,
    val startBearingDeg: Double?,
    val stopBearingDeg: Double?,
    val direction: ReadingDirection,
    val visible: Boolean,
    val createdAt: Instant,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: "Reading ${id.toString().take(7)}"

    val createdAtUtc: String
        get() = UTC_FORMATTER.format(createdAt)
}
