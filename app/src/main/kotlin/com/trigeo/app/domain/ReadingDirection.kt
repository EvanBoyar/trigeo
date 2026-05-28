package com.trigeo.app.domain

enum class ReadingDirection {
    /** Bearing extends from the point in the captured direction only. */
    NORMAL,

    /** Bearing extends in both directions (180-degree antenna ambiguity). */
    BIDIRECTIONAL,

    /** Bearing is interpreted as the opposite of the captured direction.
     *  The captured center/start/stop values are preserved unchanged; this
     *  flag flips how the reading is rendered and used downstream. */
    REVERSED,
}
