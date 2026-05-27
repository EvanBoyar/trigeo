package com.trigeo.app.domain

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude $latitude out of range" }
        require(longitude in -180.0..180.0) { "longitude $longitude out of range" }
    }
}
