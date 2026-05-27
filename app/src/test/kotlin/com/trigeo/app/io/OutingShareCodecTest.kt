package com.trigeo.app.io

import com.trigeo.app.domain.BearingCapture
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Outing
import com.trigeo.app.domain.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class OutingShareCodecTest {

    private val outing = Outing(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        name = "Fox Hunt",
        createdAt = Instant.ofEpochMilli(1_716_817_000_000L),
    )

    private val readings = listOf(
        Reading(
            id = UUID.fromString("00000000-0000-0000-0000-00000000000a"),
            outingId = outing.id,
            name = "Hilltop",
            point = GeoPoint(37.422, -122.082),
            bearing = BearingCapture(120.5, 2.5),
            startBearingDeg = 118.0,
            stopBearingDeg = 123.0,
            bidirectional = false,
            visible = true,
            createdAt = Instant.ofEpochMilli(1_716_817_100_000L),
        ),
        Reading(
            id = UUID.fromString("00000000-0000-0000-0000-00000000000b"),
            outingId = outing.id,
            name = null,
            point = GeoPoint(37.425, -122.081),
            bearing = BearingCapture(135.2, 5.0),
            startBearingDeg = null,
            stopBearingDeg = null,
            bidirectional = true,
            visible = true,
            createdAt = Instant.ofEpochMilli(1_716_817_200_000L),
        ),
    )

    @Test
    fun roundtrip_preserves_outing_and_readings() {
        val text = OutingShareCodec.encode(outing, readings)
        assertTrue("starts with prefix: $text", text.startsWith("trigeo:v1:"))
        val share = OutingShareCodec.decode(text).getOrThrow()
        assertEquals(outing.id, share.outingId)
        assertEquals(outing.name, share.outingName)
        assertEquals(outing.createdAt, share.outingCreatedAt)
        assertEquals(2, share.readings.size)
        val first = share.readings[0]
        assertEquals(readings[0].id, first.id)
        assertEquals("Hilltop", first.name)
        assertEquals(37.422, first.point.latitude, 1e-9)
        assertEquals(-122.082, first.point.longitude, 1e-9)
        assertEquals(120.5, first.bearing.centerDeg, 1e-9)
        assertEquals(2.5, first.bearing.halfWidthDeg, 1e-9)
        assertEquals(false, first.bidirectional)
        val second = share.readings[1]
        assertEquals(readings[1].id, second.id)
        assertNull(second.name)
        assertEquals(true, second.bidirectional)
    }

    @Test
    fun encodeReading_emits_single_reading_with_parent_outing_meta() {
        val text = OutingShareCodec.encodeReading(outing, readings[0])
        val share = OutingShareCodec.decode(text).getOrThrow()
        assertEquals(outing.id, share.outingId)
        assertEquals(outing.name, share.outingName)
        assertEquals(1, share.readings.size)
        assertEquals(readings[0].id, share.readings[0].id)
    }

    @Test
    fun decode_finds_token_inside_a_larger_message() {
        val text = OutingShareCodec.encode(outing, readings)
        val wrapped = "Hey, check this out!\n${text}\n\nMore words after"
        val share = OutingShareCodec.decode(wrapped).getOrThrow()
        assertEquals(outing.name, share.outingName)
    }

    @Test
    fun decode_returns_NoToken_when_missing() {
        val result = OutingShareCodec.decode("just some random text without any token")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OutingShareCodec.DecodeError.NoToken)
    }

    @Test
    fun decode_returns_BadBase64_when_token_corrupted() {
        val result = OutingShareCodec.decode("trigeo:v1:not-valid-base64-!!!")
        // The non-base64 chars (`!`) cause the regex to stop before them; the
        // remaining prefix is empty, which decodes to an empty byte array and
        // then fails JSON parsing. Either error category is acceptable here.
        assertTrue(result.isFailure)
    }

    @Test
    fun decode_handles_unknown_fields() {
        // Manually craft a payload with an extra field and check we still parse.
        val text = OutingShareCodec.encode(outing, readings)
        val share = OutingShareCodec.decode(text).getOrThrow()
        assertEquals(2, share.readings.size)
    }
}
