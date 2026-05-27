package com.trigeo.app.map

enum class MapTileStyle {
    OSM,
    OPEN_TOPO,
    ;

    val styleJson: String
        get() = when (this) {
            OSM -> TileStyles.osmRasterStyleJson
            OPEN_TOPO -> TileStyles.openTopoStyleJson
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

object TileStyles {
    val osmRasterStyleJson: String = """
        {
          "version": 8,
          "name": "OSM raster",
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "minzoom": 0,
              "maxzoom": 19,
              "attribution": "(c) OpenStreetMap contributors"
            }
          },
          "layers": [
            { "id": "osm", "type": "raster", "source": "osm" }
          ]
        }
    """.trimIndent()

    val openTopoStyleJson: String = """
        {
          "version": 8,
          "name": "OpenTopoMap",
          "sources": {
            "otm": {
              "type": "raster",
              "tiles": [
                "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
              ],
              "tileSize": 256,
              "minzoom": 0,
              "maxzoom": 17,
              "attribution": "Map data (c) OpenStreetMap contributors, SRTM | Style (c) OpenTopoMap (CC-BY-SA)"
            }
          },
          "layers": [
            { "id": "otm", "type": "raster", "source": "otm" }
          ]
        }
    """.trimIndent()
}
