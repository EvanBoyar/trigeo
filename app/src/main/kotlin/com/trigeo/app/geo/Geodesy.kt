package com.trigeo.app.geo

import com.trigeo.app.domain.GeoPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Geodesy {
    const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Great-circle destination point: starting at `from`, heading `bearingDeg`
     * true, traveling `distanceMeters`. Bearing in [0, 360), distance >= 0.
     */
    fun destination(
        from: GeoPoint,
        bearingDeg: Double,
        distanceMeters: Double,
    ): GeoPoint {
        val phi1 = Math.toRadians(from.latitude)
        val lam1 = Math.toRadians(from.longitude)
        val theta = Math.toRadians(bearingDeg)
        val delta = distanceMeters / EARTH_RADIUS_METERS

        val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
        val lam2 = lam1 + atan2(
            sin(theta) * sin(delta) * cos(phi1),
            cos(delta) - sin(phi1) * sin(phi2),
        )
        val newLat = Math.toDegrees(phi2)
        val newLon = ((Math.toDegrees(lam2) + 540.0) % 360.0) - 180.0
        return GeoPoint(newLat, newLon)
    }
}
