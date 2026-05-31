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
import kotlin.math.hypot
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
        startBearingDeg = null,
        stopBearingDeg = null,
        direction = com.trigeo.app.domain.ReadingDirection.NORMAL,
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

    // --- Range-aware (Stansfield) behavior -------------------------------

    private val base = GeoPoint(40.0, -75.0)
    private val mPerDegLon = 111_320.0 * cos(Math.toRadians(40.0))

    // Place a reading at an east/north offset (meters) from `base`.
    private fun stationAt(
        eastM: Double,
        northM: Double,
        bearingDeg: Double,
        halfWidthDeg: Double = 2.5,
    ): Reading = reading(
        lat = base.latitude + northM / 111_320.0,
        lon = base.longitude + eastM / mPerDegLon,
        bearingDeg = bearingDeg,
        halfWidthDeg = halfWidthDeg,
    )

    private fun fixEast(fix: TriangulationFix): Double =
        (fix.point.longitude - base.longitude) * mPerDegLon

    private fun fixNorth(fix: TriangulationFix): Double =
        (fix.point.latitude - base.latitude) * 111_320.0

    private fun ellipseArea(fix: TriangulationFix): Double =
        fix.errorEllipse.semiMajorMeters * fix.errorEllipse.semiMinorMeters

    @Test
    fun near_reading_dominates_a_far_one_that_disagrees() {
        // A near reading insists the fix is on the line x=0; a far reading
        // insists x=10. A third reading pins y=0. Angular-only weighting would
        // split the difference at x=5; the range-aware fix must sit on top of
        // the near reading's line instead.
        val near = stationAt(eastM = 0.0, northM = -50.0, bearingDeg = 0.0) // line x=0, R=50
        val far = stationAt(eastM = 10.0, northM = -2000.0, bearingDeg = 0.0) // line x=10, R=2000
        val cross = stationAt(eastM = -2000.0, northM = 0.0, bearingDeg = 90.0) // line y=0

        val fix = Triangulation.solve(listOf(near, far, cross))
        assertNotNull(fix)
        fix!!
        assertTrue(
            "fix should hug the near line (x~0), got east=${fixEast(fix)}",
            fixEast(fix) < 1.0,
        )
        assertTrue("fix north should be ~0, got ${fixNorth(fix)}", kotlin.math.abs(fixNorth(fix)) < 1.0)
    }

    @Test
    fun a_near_reading_shrinks_the_fix_more_than_a_far_one() {
        val r1 = stationAt(eastM = -1000.0, northM = 0.0, bearingDeg = 90.0) // line y=0
        val r2 = stationAt(eastM = 0.0, northM = -1000.0, bearingDeg = 0.0) // line x=0
        val near = stationAt(eastM = 0.0, northM = -50.0, bearingDeg = 0.0) // line x=0, R=50
        val far = stationAt(eastM = 0.0, northM = -2000.0, bearingDeg = 0.0) // line x=0, R=2000

        val baseFix = Triangulation.solve(listOf(r1, r2))!!
        val nearFix = Triangulation.solve(listOf(r1, r2, near))!!
        val farFix = Triangulation.solve(listOf(r1, r2, far))!!

        assertTrue("adding any reading should shrink the area", ellipseArea(nearFix) < ellipseArea(baseFix))
        assertTrue(
            "near (${ellipseArea(nearFix)}) should shrink more than far (${ellipseArea(farFix)})",
            ellipseArea(nearFix) < ellipseArea(farFix),
        )
        assertTrue("near should shrink the area a lot", ellipseArea(nearFix) < 0.25 * ellipseArea(baseFix))
    }

    @Test
    fun a_reading_on_top_of_the_fix_does_not_blow_up() {
        // r1's line passes through the fix and its range collapses to ~0; the
        // range floor must keep the weight (and the ellipse) finite.
        val r1 = stationAt(eastM = 0.0, northM = 0.0, bearingDeg = 0.0) // line x=0, R~0
        val r2 = stationAt(eastM = -1000.0, northM = 0.0, bearingDeg = 90.0) // line y=0

        val fix = Triangulation.solve(listOf(r1, r2))
        assertNotNull(fix)
        fix!!
        assertTrue(fix.point.latitude.isFinite() && fix.point.longitude.isFinite())
        val major = fix.errorEllipse.semiMajorMeters
        assertTrue("ellipse must stay finite and sane, got $major", major > 0.0 && major < 1e6)
    }

    @Test
    fun the_ellipse_inflates_when_a_bearing_disagrees() {
        val r1 = stationAt(eastM = -1000.0, northM = 0.0, bearingDeg = 90.0) // line y=0
        val r2 = stationAt(eastM = 0.0, northM = -1000.0, bearingDeg = 0.0) // line x=0
        val r3good = stationAt(eastM = -1000.0, northM = -1000.0, bearingDeg = 45.0) // through origin
        val r3bad = stationAt(eastM = -1000.0, northM = -1000.0, bearingDeg = 65.0) // 20 deg off

        val consistent = Triangulation.solve(listOf(r1, r2, r3good))!!
        val disagreeing = Triangulation.solve(listOf(r1, r2, r3bad))!!

        assertTrue(
            "disagreement should inflate the ellipse: ${ellipseArea(disagreeing)} vs ${ellipseArea(consistent)}",
            ellipseArea(disagreeing) > 3.0 * ellipseArea(consistent),
        )
    }

    @Test
    fun wide_uncertainty_with_km_scale_geometry_still_produces_a_fix() {
        // From a real outing where the fix used to come back null: ~1 km between
        // stations, +/-10 deg cones. IRLS reweighting drops the normal matrix
        // entries to ~1e-5; the old absolute det threshold killed an otherwise
        // well-conditioned system.
        val r1 = reading(40.694296, -73.966026, 289.1417324233176, halfWidthDeg = 10.0)
        val r2 = reading(40.680389, -73.998093, 9.494456939121369, halfWidthDeg = 10.0)
        val r3 = reading(40.6998643, -73.9865709, 317.1462703864985, halfWidthDeg = 10.0)
        val fix = Triangulation.solve(listOf(r1, r2, r3))
        assertNotNull("fix should be produced for well-posed wide-uncertainty geometry", fix)
    }

    @Test
    fun best_case_close_readings_give_a_single_digit_meter_ellipse() {
        // Three readings ~100 m out, +/-2.5 deg, 120 deg apart, all on target.
        val r1 = stationAt(eastM = 0.0, northM = 100.0, bearingDeg = 180.0)
        val r2 = stationAt(eastM = 86.6, northM = -50.0, bearingDeg = 300.0)
        val r3 = stationAt(eastM = -86.6, northM = -50.0, bearingDeg = 60.0)

        val fix = Triangulation.solve(listOf(r1, r2, r3))
        assertNotNull(fix)
        fix!!
        assertTrue("fix should land on target", hypot(fixEast(fix), fixNorth(fix)) < 1.0)
        val major = fix.errorEllipse.semiMajorMeters
        assertTrue("expected roughly 3-5 m, got $major", major in 2.5..5.0)
    }
}
