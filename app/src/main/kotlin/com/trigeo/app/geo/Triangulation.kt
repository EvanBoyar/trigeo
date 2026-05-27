package com.trigeo.app.geo

import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Reading
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Weighted least-squares triangulation.
 *
 * Each visible bearing defines a line in a local east-north (ENU) frame
 * anchored at the centroid of the input points. We minimize the sum of
 * weighted squared perpendicular distances from the unknown fix point to
 * those lines. Weights are 1 / halfWidth^2 (clamped) so tight bearings
 * pull the fix harder.
 *
 * Bidirectional readings produce the same line as forward-only ones (a
 * line is direction-symmetric), so they participate the same way; the
 * solver does not penalize fixes that fall behind the operator. If the
 * matrix is near-singular (all bearings nearly parallel), returns null.
 */
data class TriangulationFix(
    val point: GeoPoint,
    val errorEllipse: ErrorEllipse,
    val readingCount: Int,
)

data class ErrorEllipse(
    val semiMajorMeters: Double,
    val semiMinorMeters: Double,
    val orientationDeg: Double,
)

object Triangulation {

    private const val METERS_PER_DEG_LAT = 111_320.0
    private const val MIN_HALF_WIDTH_DEG = 0.5

    fun solve(readings: List<Reading>): TriangulationFix? {
        if (readings.size < 2) return null

        val refLat = readings.sumOf { it.point.latitude } / readings.size
        val refLon = readings.sumOf { it.point.longitude } / readings.size
        val mPerDegLon = METERS_PER_DEG_LAT * cos(Math.toRadians(refLat))

        data class Row(val x: Double, val y: Double, val nx: Double, val ny: Double, val w: Double)

        val rows = readings.map { r ->
            val x = (r.point.longitude - refLon) * mPerDegLon
            val y = (r.point.latitude - refLat) * METERS_PER_DEG_LAT
            val theta = Math.toRadians(r.bearing.centerDeg)
            val nx = cos(theta)
            val ny = -sin(theta)
            val hw = r.bearing.halfWidthDeg.coerceAtLeast(MIN_HALF_WIDTH_DEG)
            Row(x, y, nx, ny, 1.0 / (hw * hw))
        }

        var a00 = 0.0
        var a01 = 0.0
        var a11 = 0.0
        var b0 = 0.0
        var b1 = 0.0
        for (row in rows) {
            val nDotP = row.nx * row.x + row.ny * row.y
            a00 += row.w * row.nx * row.nx
            a01 += row.w * row.nx * row.ny
            a11 += row.w * row.ny * row.ny
            b0 += row.w * nDotP * row.nx
            b1 += row.w * nDotP * row.ny
        }

        val det = a00 * a11 - a01 * a01
        if (abs(det) < 1e-9) return null
        val invDet = 1.0 / det
        val solX = invDet * (a11 * b0 - a01 * b1)
        val solY = invDet * (-a01 * b0 + a00 * b1)

        var rss = 0.0
        for (row in rows) {
            val res = row.nx * (solX - row.x) + row.ny * (solY - row.y)
            rss += row.w * res * res
        }
        val sigma2 = if (rows.size > 2) rss / (rows.size - 2) else 1.0

        val c00 = sigma2 * a11 * invDet
        val c01 = -sigma2 * a01 * invDet
        val c11 = sigma2 * a00 * invDet

        val tr = c00 + c11
        val det2 = c00 * c11 - c01 * c01
        val disc = sqrt(max(0.0, tr * tr / 4 - det2))
        val lambda1 = tr / 2 + disc
        val lambda2 = tr / 2 - disc
        val semiMajor = sqrt(max(0.0, lambda1))
        val semiMinor = sqrt(max(0.0, lambda2))

        val vx: Double
        val vy: Double
        if (abs(c01) > 1e-12) {
            vx = -c01
            vy = c00 - lambda1
        } else if (c00 >= c11) {
            vx = 1.0; vy = 0.0
        } else {
            vx = 0.0; vy = 1.0
        }
        val orientationRad = kotlin.math.atan2(vx, vy)
        val orientationDeg = Angles.normalize(Math.toDegrees(orientationRad))

        val solLat = refLat + solY / METERS_PER_DEG_LAT
        val solLon = refLon + solX / mPerDegLon

        return TriangulationFix(
            point = GeoPoint(solLat, solLon),
            errorEllipse = ErrorEllipse(
                semiMajorMeters = semiMajor,
                semiMinorMeters = semiMinor,
                orientationDeg = orientationDeg,
            ),
            readingCount = readings.size,
        )
    }
}
