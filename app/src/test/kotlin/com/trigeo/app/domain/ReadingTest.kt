package com.trigeo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.util.TimeZone
import java.util.UUID

class ReadingTest {

    private fun reading(
        id: UUID,
        name: String? = null,
        createdAt: Instant = Instant.parse("2026-05-28T14:32:07Z"),
    ) = Reading(
        id = id,
        outingId = UUID.fromString("00000000-0000-0000-0000-000000000099"),
        name = name,
        point = GeoPoint(0.0, 0.0),
        bearing = BearingCapture(0.0, 1.0),
        startBearingDeg = null,
        stopBearingDeg = null,
        direction = ReadingDirection.NORMAL,
        visible = true,
        createdAt = createdAt,
    )

    @Test
    fun displayName_uses_first_seven_chars_of_id_when_unnamed() {
        val r = reading(UUID.fromString("a3f9c12e-0000-0000-0000-000000000000"))
        assertEquals("Reading a3f9c12", r.displayName)
    }

    @Test
    fun displayName_prefers_a_set_name() {
        val r = reading(UUID.fromString("a3f9c12e-0000-0000-0000-000000000000"), name = "Hilltop")
        assertEquals("Hilltop", r.displayName)
    }

    @Test
    fun unnamed_readings_with_same_time_but_different_ids_do_not_collide() {
        val a = reading(UUID.fromString("aaaaaaa1-0000-0000-0000-000000000000"))
        val b = reading(UUID.fromString("bbbbbbb2-0000-0000-0000-000000000000"))
        assertNotEquals(a.displayName, b.displayName)
    }

    @Test
    fun createdAtUtc_is_formatted_in_utc_regardless_of_default_zone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val r = reading(
                UUID.fromString("a3f9c12e-0000-0000-0000-000000000000"),
                createdAt = Instant.parse("2026-05-28T14:32:07Z"),
            )
            assertEquals("2026-05-28 14:32:07 UTC", r.createdAtUtc)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
