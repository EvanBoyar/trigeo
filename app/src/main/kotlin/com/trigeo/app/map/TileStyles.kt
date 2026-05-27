package com.trigeo.app.map

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
}
