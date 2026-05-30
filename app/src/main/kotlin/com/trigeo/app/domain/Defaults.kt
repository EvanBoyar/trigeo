package com.trigeo.app.domain

object Defaults {
    const val UNCERTAINTY_DEG: Double = 20.0
    const val UNCERTAINTY_MIN_DEG: Double = 1.0
    const val UNCERTAINTY_MAX_DEG: Double = 80.0
    const val BEARING_LINE_METERS: Double = 500_000.0

    /**
     * Close-range floor (meters) for the triangulation fix. A reading nearer
     * than this to the current fix is weighted as if it were this far away, so
     * a single very close bearing (where the antenna often gets unreliable)
     * cannot completely dominate the solution.
     */
    const val MIN_FIX_RANGE_METERS: Double = 25.0
}
