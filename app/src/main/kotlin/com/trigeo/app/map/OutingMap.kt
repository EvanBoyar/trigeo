package com.trigeo.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trigeo.app.domain.Defaults
import com.trigeo.app.domain.GeoPoint
import com.trigeo.app.domain.Reading
import com.trigeo.app.geo.Angles
import com.trigeo.app.geo.Geodesy
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val SRC_POINTS = "trigeo-points"
private const val SRC_LINES = "trigeo-lines"
private const val SRC_CONES = "trigeo-cones"
private const val LYR_POINTS = "trigeo-points-lyr"
private const val LYR_LINES = "trigeo-lines-lyr"
private const val LYR_CONES = "trigeo-cones-lyr"

@Composable
fun OutingMap(
    readings: List<Reading>,
    cameraTarget: GeoPoint?,
    cameraZoom: Double = 14.0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var styleRef by remember { mutableStateOf<Style?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            getMapAsync { map ->
                mapRef = map
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(20.0, 0.0))
                    .zoom(1.5)
                    .build()
                map.setStyle(Style.Builder().fromJson(TileStyles.osmRasterStyleJson)) { style ->
                    addOverlayLayers(style)
                    styleRef = style
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(readings, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        pushOverlayData(style, readings)
    }

    LaunchedEffect(cameraTarget, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        if (cameraTarget != null) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(cameraTarget.latitude, cameraTarget.longitude),
                    cameraZoom,
                ),
            )
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addOverlayLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_CONES))
    style.addSource(GeoJsonSource(SRC_LINES))
    style.addSource(GeoJsonSource(SRC_POINTS))

    style.addLayer(
        FillLayer(LYR_CONES, SRC_CONES).withProperties(
            fillColor("#F2C94C"),
            fillOpacity(0.22f),
        ),
    )
    style.addLayer(
        LineLayer(LYR_LINES, SRC_LINES).withProperties(
            lineColor("#1F4E79"),
            lineWidth(3f),
            lineOpacity(0.9f),
        ),
    )
    style.addLayer(
        CircleLayer(LYR_POINTS, SRC_POINTS).withProperties(
            circleRadius(7f),
            circleColor("#1F4E79"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(2.5f),
        ),
    )
}

private fun pushOverlayData(style: Style, readings: List<Reading>) {
    val visible = readings.filter { it.visible }

    val points = visible.map { r ->
        Feature.fromGeometry(Point.fromLngLat(r.point.longitude, r.point.latitude))
    }
    (style.getSource(SRC_POINTS) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(points))

    val lines = visible.flatMap { r -> buildBearingLines(r) }
    (style.getSource(SRC_LINES) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(lines))

    val cones = visible.flatMap { r -> buildConePolygons(r) }
    (style.getSource(SRC_CONES) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(cones))
}

private fun buildBearingLines(reading: Reading): List<Feature> {
    val origin = reading.point
    val originPt = Point.fromLngLat(origin.longitude, origin.latitude)
    val forwardEnd = Geodesy.destination(origin, reading.bearing.centerDeg, Defaults.BEARING_LINE_METERS)
    val forward = Feature.fromGeometry(
        LineString.fromLngLats(
            listOf(originPt, Point.fromLngLat(forwardEnd.longitude, forwardEnd.latitude)),
        ),
    )
    if (!reading.bidirectional) return listOf(forward)
    val backEnd = Geodesy.destination(
        origin,
        Angles.normalize(reading.bearing.centerDeg + 180.0),
        Defaults.BEARING_LINE_METERS,
    )
    val backward = Feature.fromGeometry(
        LineString.fromLngLats(
            listOf(originPt, Point.fromLngLat(backEnd.longitude, backEnd.latitude)),
        ),
    )
    return listOf(forward, backward)
}

private fun buildConePolygons(reading: Reading): List<Feature> {
    val forward = buildConePolygon(reading, reading.bearing.centerDeg)
    if (!reading.bidirectional) return listOf(forward)
    val backward = buildConePolygon(reading, Angles.normalize(reading.bearing.centerDeg + 180.0))
    return listOf(forward, backward)
}

private fun buildConePolygon(reading: Reading, centerDeg: Double): Feature {
    val origin = reading.point
    val steps = 12
    val ring = mutableListOf(Point.fromLngLat(origin.longitude, origin.latitude))
    val halfWidth = reading.bearing.halfWidthDeg
    val start = Angles.normalize(centerDeg - halfWidth)
    val span = halfWidth * 2.0
    for (i in 0..steps) {
        val deg = start + span * (i.toDouble() / steps)
        val e = Geodesy.destination(origin, deg, Defaults.BEARING_LINE_METERS)
        ring.add(Point.fromLngLat(e.longitude, e.latitude))
    }
    ring.add(Point.fromLngLat(origin.longitude, origin.latitude))
    return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
}
