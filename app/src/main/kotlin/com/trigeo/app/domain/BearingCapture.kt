package com.trigeo.app.domain

import com.trigeo.app.geo.Angles

/**
 * A bearing observation. Stored as TRUE north (declination already applied).
 *
 *  - `centerDeg` is the main bearing, in degrees true, in [0, 360).
 *  - `halfWidthDeg` is half of the uncertainty cone, in [0, 180].
 *
 * The "start" and "stop" representation (two bearings bracketing the signal)
 * is the same data, recoverable from these two fields and presented in the
 * UI as needed.
 */
data class BearingCapture(
    val centerDeg: Double,
    val halfWidthDeg: Double,
) {
    init {
        require(centerDeg in 0.0..360.0) { "centerDeg $centerDeg out of range" }
        require(halfWidthDeg in 0.0..180.0) { "halfWidthDeg $halfWidthDeg out of range" }
    }

    val uncertaintyDeg: Double get() = halfWidthDeg * 2.0
    val startDeg: Double get() = Angles.normalize(centerDeg - halfWidthDeg)
    val stopDeg: Double get() = Angles.normalize(centerDeg + halfWidthDeg)

    companion object {
        fun fromCenter(centerDeg: Double, uncertaintyDeg: Double): BearingCapture =
            BearingCapture(
                centerDeg = Angles.normalize(centerDeg),
                halfWidthDeg = (uncertaintyDeg / 2.0).coerceIn(0.0, 180.0),
            )

        fun fromStartStop(startDeg: Double, stopDeg: Double): BearingCapture {
            val r = Angles.bisector(startDeg, stopDeg)
            return BearingCapture(r.centerDeg, r.halfWidthDeg)
        }
    }
}
