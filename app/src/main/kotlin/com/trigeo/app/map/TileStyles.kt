package com.trigeo.app.map

enum class MapTileStyle {
    OSM,
    OPEN_TOPO,
    ;

    val styleUri: String
        get() = when (this) {
            OSM -> "asset://styles/osm.json"
            OPEN_TOPO -> "asset://styles/opentopo.json"
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
