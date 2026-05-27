package com.trigeo.app.map

import android.content.Context
import com.trigeo.app.TrigeoApp

enum class MapTileStyle {
    OSM,
    OPEN_TOPO,
    ;

    private val filename: String
        get() = when (this) {
            OSM -> "osm.json"
            OPEN_TOPO -> "opentopo.json"
        }

    fun styleUri(context: Context): String {
        val app = context.applicationContext as TrigeoApp
        val port = app.styleServer.port
        return "http://127.0.0.1:$port/$filename"
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
