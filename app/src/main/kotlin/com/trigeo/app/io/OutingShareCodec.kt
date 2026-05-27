package com.trigeo.app.io

import com.trigeo.app.domain.BearingCapture
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Outing
import com.trigeo.app.domain.Reading
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Encodes an outing plus its readings into a single text string suitable for
 * pasting into an SMS or chat. Format:
 *
 *   trigeo:v1:<urlsafe-base64 of compact JSON>
 *
 * Decoding accepts either the bare prefixed string or a larger blob of text
 * that contains the prefix somewhere inside it, so users can paste the whole
 * message they received without surgery.
 */
object OutingShareCodec {

    private const val PREFIX = "trigeo:v1:"
    private const val PAYLOAD_CHARS = "A-Za-z0-9_\\-"
    private val tokenRegex = Regex("trigeo:v1:([${PAYLOAD_CHARS}]+)")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(outing: Outing, readings: List<Reading>): String {
        val payload = OutingPayload(
            id = outing.id.toString(),
            name = outing.name,
            createdAt = outing.createdAt.toEpochMilli(),
            readings = readings.map { it.toPayload() },
        )
        return encode(payload)
    }

    fun encodeReading(parentOuting: Outing, reading: Reading): String {
        val payload = OutingPayload(
            id = parentOuting.id.toString(),
            name = parentOuting.name,
            createdAt = parentOuting.createdAt.toEpochMilli(),
            readings = listOf(reading.toPayload()),
        )
        return encode(payload)
    }

    private fun encode(payload: OutingPayload): String {
        val body = json.encodeToString(OutingPayload.serializer(), payload)
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray(Charsets.UTF_8))
        return "$PREFIX$b64"
    }

    private fun Reading.toPayload(): ReadingPayload = ReadingPayload(
        id = id.toString(),
        name = name,
        lat = point.latitude,
        lon = point.longitude,
        bearingDeg = bearing.centerDeg,
        halfWidthDeg = bearing.halfWidthDeg,
        bidirectional = if (bidirectional) 1 else 0,
        createdAt = createdAt.toEpochMilli(),
    )

    sealed class DecodeError(message: String) : Exception(message) {
        object NoToken : DecodeError("No Trigeo share token found in the text.")
        object BadBase64 : DecodeError("The share token is corrupted (not valid base64).")
        class BadJson(cause: Throwable) : DecodeError("The share token doesn't decode to a valid payload: ${cause.message}")
    }

    fun decode(text: String): Result<OutingShare> {
        val match = tokenRegex.find(text) ?: return Result.failure(DecodeError.NoToken)
        val b64 = match.groupValues[1]
        val bytes = runCatching {
            Base64.getUrlDecoder().decode(b64)
        }.getOrElse { return Result.failure(DecodeError.BadBase64) }
        val payload = runCatching {
            json.decodeFromString(OutingPayload.serializer(), String(bytes, Charsets.UTF_8))
        }.getOrElse { return Result.failure(DecodeError.BadJson(it)) }
        return Result.success(payload.toShare())
    }

    @Serializable
    private data class OutingPayload(
        @SerialName("i") val id: String? = null,
        @SerialName("n") val name: String? = null,
        @SerialName("t") val createdAt: Long,
        @SerialName("r") val readings: List<ReadingPayload> = emptyList(),
    ) {
        fun toShare(): OutingShare = OutingShare(
            outingId = id?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            outingName = name,
            outingCreatedAt = Instant.ofEpochMilli(createdAt),
            readings = readings.map { it.toShare() },
        )
    }

    @Serializable
    private data class ReadingPayload(
        @SerialName("i") val id: String? = null,
        @SerialName("n") val name: String? = null,
        @SerialName("la") val lat: Double,
        @SerialName("lo") val lon: Double,
        @SerialName("b") val bearingDeg: Double,
        @SerialName("h") val halfWidthDeg: Double,
        @SerialName("d") val bidirectional: Int = 0,
        @SerialName("t") val createdAt: Long,
    ) {
        fun toShare(): ReadingShare = ReadingShare(
            id = id?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            name = name,
            point = GeoPoint(lat, lon),
            bearing = BearingCapture(bearingDeg.coerceIn(0.0, 360.0), halfWidthDeg.coerceIn(0.0, 180.0)),
            bidirectional = bidirectional != 0,
            createdAt = Instant.ofEpochMilli(createdAt),
        )
    }
}

data class OutingShare(
    val outingId: UUID?,
    val outingName: String?,
    val outingCreatedAt: Instant,
    val readings: List<ReadingShare>,
)

data class ReadingShare(
    val id: UUID?,
    val name: String?,
    val point: GeoPoint,
    val bearing: BearingCapture,
    val bidirectional: Boolean,
    val createdAt: Instant,
)
