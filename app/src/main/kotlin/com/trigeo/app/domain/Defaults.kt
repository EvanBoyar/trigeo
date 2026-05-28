package com.trigeo.app.domain

object Defaults {
    const val UNCERTAINTY_DEG: Double = 5.0
    const val BEARING_LINE_METERS: Double = 50_000.0

    /**
     * Close-range floor (meters) for the triangulation fix. A reading nearer
     * than this to the current fix is weighted as if it were this far away, so
     * a single very close bearing (where the antenna often gets unreliable)
     * cannot completely dominate the solution.
     */
    const val MIN_FIX_RANGE_METERS: Double = 25.0
}
