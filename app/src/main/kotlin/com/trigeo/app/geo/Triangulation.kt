package com.trigeo.app.geo

import com.trigeo.app.domain.Defaults
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Reading
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stansfield maximum-likelihood triangulation, solved by iteratively reweighted
 * least squares (IRLS).
 *
 * Each visible bearing defines a line in a local east-north (ENU) frame anchored
 * at the centroid of the input points. We minimize the sum of weighted squared
 * perpendicular distances from the unknown fix to those lines, but the weight is
 * 1 / (sigma^2 * R^2), where sigma is the angular uncertainty (radians) and R is
 * the range from the current fix to that reading's point. The R term is the key
 * difference from a plain angular fit: a fixed angular error turns into a
 * cross-range position error that grows with distance, so a close reading is a
 * far tighter position constraint and pulls the fix harder (by 1/R^2). Because R
 * depends on the fix we are solving for, we iterate: start from the angular-only
 * intersection, then reweight and re-solve until the fix stops moving.
 *
 * R is floored at minRangeMeters so a reading sitting on top of the fix (where
 * the antenna often swings wildly) cannot blow its weight up to infinity.
 *
 * The error ellipse is the inverse Fisher information (an absolute covariance in
 * meters, driven by the stated uncertainties and the geometry), inflated by the
 * reduced chi-square when the bearings disagree. It never shrinks below the
 * absolute floor, so it will not claim more precision than the uncertainties
 * justify, but it grows to flag a bad reading.
 *
 * Bidirectional readings produce the same line as forward-only ones (a line is
 * direction-symmetric), so they participate the same way. If the system is
 * near-singular (all bearings nearly parallel), returns null.
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
    private const val MAX_IRLS_ITER = 10
    private const val CONVERGENCE_M = 0.1

    // Scale-free "near-singular" test for the 2x2 normal matrix A. For a
    // well-conditioned A with eigenvalues l1 ~= l2, det(A)/trace(A)^2 ~= 0.25; for
    // a truly rank-deficient A it is 0. An absolute threshold on det fails when
    // 1/R^2 weighting shrinks every entry by ~1e-6 to 1e-10, even though the
    // geometry is fine.
    private const val SINGULAR_DET_RATIO = 1e-12

    private class Row(
        val x: Double,
        val y: Double,
        val nx: Double,
        val ny: Double,
        val sigma: Double,
    )

    fun solve(
        readings: List<Reading>,
        minRangeMeters: Double = Defaults.MIN_FIX_RANGE_METERS,
    ): TriangulationFix? {
        if (readings.size < 2) return null

        val refLat = readings.sumOf { it.point.latitude } / readings.size
        val refLon = readings.sumOf { it.point.longitude } / readings.size
        val mPerDegLon = METERS_PER_DEG_LAT * cos(Math.toRadians(refLat))
        val rMin = minRangeMeters.coerceAtLeast(1.0)

        val rows = readings.map { r ->
            val x = (r.point.longitude - refLon) * mPerDegLon
            val y = (r.point.latitude - refLat) * METERS_PER_DEG_LAT
            val theta = Math.toRadians(r.bearing.centerDeg)
            val nx = cos(theta)
            val ny = -sin(theta)
            val sigma = Math.toRadians(r.bearing.halfWidthDeg.coerceAtLeast(MIN_HALF_WIDTH_DEG))
            Row(x, y, nx, ny, sigma)
        }

        // Initial fix from angular-only weights (1/sigma^2): the classic
        // weighted intersection, used to seed the range-dependent reweighting.
        val angularWeights = DoubleArray(rows.size) { 1.0 / (rows[it].sigma * rows[it].sigma) }
        val initial = solveWeighted(rows, angularWeights) ?: return null
        var solX = initial.first
        var solY = initial.second

        // IRLS: reweight by 1/(sigma^2 * R^2) using the range from the current
        // fix to each station, floored at rMin, then re-solve until it settles.
        val weights = DoubleArray(rows.size)
        for (iter in 0 until MAX_IRLS_ITER) {
            for (i in rows.indices) {
                val row = rows[i]
                val r = hypot(solX - row.x, solY - row.y).coerceAtLeast(rMin)
                weights[i] = 1.0 / (row.sigma * row.sigma * r * r)
            }
            val next = solveWeighted(rows, weights) ?: break
            val shift = hypot(next.first - solX, next.second - solY)
            solX = next.first
            solY = next.second
            if (shift < CONVERGENCE_M) break
        }

        // Fisher information A from the converged weights; A^-1 is the absolute
        // covariance in meters^2. rss feeds the reduced chi-square.
        var a00 = 0.0
        var a01 = 0.0
        var a11 = 0.0
        var rss = 0.0
        for (i in rows.indices) {
            val row = rows[i]
            val w = weights[i]
            a00 += w * row.nx * row.nx
            a01 += w * row.nx * row.ny
            a11 += w * row.ny * row.ny
            val res = row.nx * (solX - row.x) + row.ny * (solY - row.y)
            rss += w * res * res
        }
        val traceA = a00 + a11
        val det = a00 * a11 - a01 * a01
        if (traceA <= 0.0 || det < SINGULAR_DET_RATIO * traceA * traceA) return null
        val invDet = 1.0 / det

        // Hybrid covariance: absolute floor, inflated by reduced chi-square on
        // disagreement, never below the floor.
        val dof = rows.size - 2
        val reducedChiSq = if (dof > 0) rss / dof else 1.0
        val scale = max(1.0, reducedChiSq)
        val c00 = scale * a11 * invDet
        val c01 = -scale * a01 * invDet
        val c11 = scale * a00 * invDet

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

    /**
     * Solve the weighted 2x2 normal equations for the fix point that minimizes
     * the sum of weighted squared perpendicular distances to the bearing lines.
     * Returns null if the system is near-singular (bearings nearly parallel).
     */
    private fun solveWeighted(rows: List<Row>, weights: DoubleArray): Pair<Double, Double>? {
        var a00 = 0.0
        var a01 = 0.0
        var a11 = 0.0
        var b0 = 0.0
        var b1 = 0.0
        for (i in rows.indices) {
            val row = rows[i]
            val w = weights[i]
            val nDotP = row.nx * row.x + row.ny * row.y
            a00 += w * row.nx * row.nx
            a01 += w * row.nx * row.ny
            a11 += w * row.ny * row.ny
            b0 += w * nDotP * row.nx
            b1 += w * nDotP * row.ny
        }
        val tr = a00 + a11
        val det = a00 * a11 - a01 * a01
        if (tr <= 0.0 || det < SINGULAR_DET_RATIO * tr * tr) return null
        val invDet = 1.0 / det
        return Pair(
            invDet * (a11 * b0 - a01 * b1),
            invDet * (-a01 * b0 + a00 * b1),
        )
    }
}
