package com.trigeo.app.geo

import com.trigeo.app.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GeodesyTest {

    @Test
    fun north_destination_increases_latitude() {
        val start = GeoPoint(40.0, -75.0)
        val end = Geodesy.destination(start, bearingDeg = 0.0, distanceMeters = 100_000.0)
        // 100 km north is approximately 0.9 degrees latitude.
        assertEquals(start.latitude + 0.899, end.latitude, 0.01)
        assertEquals(start.longitude, end.longitude, 0.0001)
    }

    @Test
    fun east_destination_increases_longitude() {
        val start = GeoPoint(0.0, 0.0)
        val end = Geodesy.destination(start, bearingDeg = 90.0, distanceMeters = 111_000.0)
        // Near the equator, 111 km east is approximately 1 degree of longitude.
        assertEquals(0.0, end.latitude, 0.001)
        assertEquals(0.997, end.longitude, 0.01)
    }

    @Test
    fun zero_distance_returns_same_point() {
        val start = GeoPoint(37.42, -122.08)
        val end = Geodesy.destination(start, bearingDeg = 123.0, distanceMeters = 0.0)
        assertEquals(start.latitude, end.latitude, 1e-9)
        assertEquals(start.longitude, end.longitude, 1e-9)
    }
}
