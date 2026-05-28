package com.trigeo.app.sensors

/** Debug-only compass state forced via MainActivity launch extras; ignored in release builds. */
object DebugCompassOverride {
    @Volatile var forceNoCompass: Boolean = false
    @Volatile var forcedAccuracy: CompassAccuracy? = null
}
