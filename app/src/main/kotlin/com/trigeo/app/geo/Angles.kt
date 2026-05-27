package com.trigeo.app.geo

import kotlin.math.IEEErem

/**
 * Helpers for working with compass bearings in degrees.
 *
 * Conventions:
 *  - "bearing" is a compass heading measured clockwise from north, in degrees.
 *  - Normalized bearings are in the half-open range [0, 360).
 *  - Signed angular differences are in [-180, 180].
 */
object Angles {

    /** Wrap any double into the canonical [0, 360) range. */
    fun normalize(deg: Double): Double {
        val r = deg.IEEErem(360.0)
        return if (r < 0) r + 360.0 else r
    }

    /** Signed shortest angular difference `to - from`, in [-180, 180]. */
    fun signedDelta(from: Double, to: Double): Double {
        val d = normalize(to - from)
        return if (d > 180.0) d - 360.0 else d
    }

    /** Always-positive angular distance between two bearings, in [0, 180]. */
    fun absDelta(a: Double, b: Double): Double = kotlin.math.abs(signedDelta(a, b))

    /**
     * Bisector of a "start" and "stop" bearing taken at the edges of an
     * angular sector. Returns the center bearing and the half-width.
     *
     * Sweeps the short way around from `start` to `stop`. If start == stop,
     * returns (start, 0). If start and stop are antipodal (180 degrees apart),
     * the bisector is ambiguous; we pick the clockwise interpretation.
     */
    fun bisector(start: Double, stop: Double): BisectorResult {
        val s = normalize(start)
        val e = normalize(stop)
        var width = e - s
        if (width < 0) width += 360.0
        if (width > 360.0) width -= 360.0
        // Sweep from start CW to stop covers `width`. If width > 180, the
        // short sweep is the other way; flip and re-anchor.
        val halfWidth: Double
        val center: Double
        if (width <= 180.0) {
            halfWidth = width / 2.0
            center = normalize(s + halfWidth)
        } else {
            val shortSweep = 360.0 - width
            halfWidth = shortSweep / 2.0
            center = normalize(e + halfWidth)
        }
        return BisectorResult(centerDeg = center, halfWidthDeg = halfWidth)
    }

    data class BisectorResult(val centerDeg: Double, val halfWidthDeg: Double)
}
