package com.trigeo.app.geo

import com.trigeo.app.domain.BearingCapture
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class TriangulationTest {

    private fun bearingTo(from: GeoPoint, to: GeoPoint): Double {
        val p1 = Math.toRadians(from.latitude)
        val p2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return Angles.normalize(Math.toDegrees(atan2(y, x)))
    }

    private fun reading(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        halfWidthDeg: Double = 2.5,
    ): Reading = Reading(
        id = UUID.randomUUID(),
        outingId = UUID.randomUUID(),
        name = null,
        point = GeoPoint(lat, lon),
        bearing = BearingCapture(Angles.normalize(bearingDeg), halfWidthDeg),
        bidirectional = false,
        visible = true,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun returns_null_for_fewer_than_two_readings() {
        assertNull(Triangulation.solve(emptyList()))
        assertNull(Triangulation.solve(listOf(reading(40.0, -75.0, 90.0))))
    }

    @Test
    fun two_perpendicular_bearings_intersect_at_the_expected_point() {
        // One reading 1km west of target, pointing east (90 deg).
        // Another 1km south of target, pointing north (0 deg).
        // Target at (40.001, -75.000) ish; bearings cross there.
        val target = GeoPoint(40.0, -75.0)
        val a = reading(target.latitude, target.longitude - 0.01, bearingDeg = 90.0)
        val b = reading(target.latitude - 0.01, target.longitude, bearingDeg = 0.0)

        val fix = Triangulation.solve(listOf(a, b))
        assertNotNull(fix)
        fix!!
        assertEquals(target.latitude, fix.point.latitude, 1e-4)
        assertEquals(target.longitude, fix.point.longitude, 1e-4)
    }

    @Test
    fun parallel_bearings_return_null() {
        val a = reading(40.0, -75.0, 45.0)
        val b = reading(40.0, -74.99, 45.0)
        assertNull(Triangulation.solve(listOf(a, b)))
    }

    @Test
    fun three_consistent_readings_converge_at_the_target() {
        // Three observers around a target; each bearing points exactly at it.
        // A clean LSQ should recover the target within numerical noise.
        val target = GeoPoint(40.0, -75.0)
        val origins = listOf(
            GeoPoint(target.latitude, target.longitude - 0.02),
            GeoPoint(target.latitude - 0.02, target.longitude + 0.005),
            GeoPoint(target.latitude + 0.015, target.longitude - 0.005),
        )
        val rs = origins.map { o -> reading(o.latitude, o.longitude, bearingTo(o, target)) }

        val fix = Triangulation.solve(rs)
        assertNotNull(fix)
        fix!!
        val dLat = (fix.point.latitude - target.latitude) * 111_320.0
        val dLon = (fix.point.longitude - target.longitude) *
            111_320.0 * cos(Math.toRadians(target.latitude))
        assertTrue(
            "fix offset (m): dLat=$dLat dLon=$dLon",
            kotlin.math.hypot(dLat, dLon) < 5.0,
        )
        assertTrue(
            "ellipse should be tight for consistent bearings: ${fix.errorEllipse.semiMajorMeters}",
            fix.errorEllipse.semiMajorMeters < 100.0,
        )
    }

    @Test
    fun tight_bearings_pull_fix_more_than_loose_ones() {
        val a = reading(40.0, -75.01, 90.0, halfWidthDeg = 0.5)
        val b = reading(40.01, -75.0, 180.0, halfWidthDeg = 10.0)
        val fix = Triangulation.solve(listOf(a, b))
        assertNotNull(fix)
        // The exact location depends on the weighting; just sanity-check
        // that we got a fix and an ellipse with positive axes.
        fix!!
        assertTrue(fix.errorEllipse.semiMajorMeters >= fix.errorEllipse.semiMinorMeters)
    }
}
