package com.trigeo.app.domain

import java.time.Instant
import java.util.UUID

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
            ?: "Reading ${createdAt.toEpochMilli().toString().takeLast(4)}"
}
