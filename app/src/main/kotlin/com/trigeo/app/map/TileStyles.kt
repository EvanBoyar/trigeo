package com.trigeo.app.map

import android.content.Context
import android.net.Uri
import java.io.File

enum class MapTileStyle {
    OSM,
    OPEN_TOPO,
    ;

    fun styleUri(context: Context): String {
        val filename = when (this) {
            OSM -> "osm.json"
            OPEN_TOPO -> "opentopo.json"
        }
        val file = File(File(context.applicationContext.filesDir, "styles"), filename)
        return Uri.fromFile(file).toString()
    }

    val maxOfflineZoom: Double
        get() = when (this) {
            OSM -> 18.0
            OPEN_TOPO -> 17.0
        }

    val displayName: String
        get() = when (this) {
            OSM -> "OpenStreetMap"
            OPEN_TOPO -> "OpenTopoMap"
        }

    val description: String
        get() = when (this) {
            OSM -> "Streets, towns, roads."
            OPEN_TOPO -> "Topographic. Contours, terrain shading, trails."
        }
}
