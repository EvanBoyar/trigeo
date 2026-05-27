package com.trigeo.app.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class AnglesTest {

    @Test
    fun normalize_wraps_positive_and_negative() {
        assertEquals(0.0, Angles.normalize(0.0), 1e-9)
        assertEquals(359.0, Angles.normalize(-1.0), 1e-9)
        assertEquals(1.0, Angles.normalize(361.0), 1e-9)
        assertEquals(10.0, Angles.normalize(370.0), 1e-9)
        assertEquals(180.0, Angles.normalize(-180.0), 1e-9)
        assertEquals(0.0, Angles.normalize(360.0), 1e-9)
    }

    @Test
    fun signedDelta_handles_wraparound() {
        assertEquals(10.0, Angles.signedDelta(350.0, 0.0), 1e-9)
        assertEquals(-10.0, Angles.signedDelta(0.0, 350.0), 1e-9)
        assertEquals(0.0, Angles.signedDelta(45.0, 45.0), 1e-9)
        assertEquals(180.0, Angles.signedDelta(0.0, 180.0), 1e-9)
        assertEquals(-179.0, Angles.signedDelta(0.0, 181.0), 1e-9)
    }

    @Test
    fun bisector_short_way() {
        val r = Angles.bisector(10.0, 50.0)
        assertEquals(30.0, r.centerDeg, 1e-9)
        assertEquals(20.0, r.halfWidthDeg, 1e-9)
    }

    @Test
    fun bisector_wraps_across_north() {
        val r = Angles.bisector(350.0, 10.0)
        assertEquals(0.0, r.centerDeg, 1e-9)
        assertEquals(10.0, r.halfWidthDeg, 1e-9)
    }

    @Test
    fun bisector_picks_the_short_arc_even_if_listed_reversed() {
        val r = Angles.bisector(10.0, 350.0)
        // The short arc from 10 CCW to 350 is 20 degrees, centered on 0.
        assertEquals(0.0, r.centerDeg, 1e-9)
        assertEquals(10.0, r.halfWidthDeg, 1e-9)
    }

    @Test
    fun bisector_equal_inputs_have_zero_width() {
        val r = Angles.bisector(123.0, 123.0)
        assertEquals(123.0, r.centerDeg, 1e-9)
        assertEquals(0.0, r.halfWidthDeg, 1e-9)
    }

    @Test
    fun bisector_antipodal_half_width_is_90() {
        val r = Angles.bisector(0.0, 180.0)
        assertEquals(90.0, r.halfWidthDeg, 1e-9)
        // Center is on whichever arc we chose; both 90 and 270 are valid.
        val ok = kotlin.math.abs(Angles.signedDelta(r.centerDeg, 90.0)) < 1e-9 ||
            kotlin.math.abs(Angles.signedDelta(r.centerDeg, 270.0)) < 1e-9
        assertEquals(true, ok)
    }
}
