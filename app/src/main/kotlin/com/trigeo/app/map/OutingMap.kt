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
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
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

private const val SRC_LIVE_POINT = "trigeo-live-point"
private const val SRC_LIVE_LINE = "trigeo-live-line"
private const val SRC_LIVE_CONE = "trigeo-live-cone"
private const val LYR_LIVE_POINT = "trigeo-live-point-lyr"
private const val LYR_LIVE_LINE = "trigeo-live-line-lyr"
private const val LYR_LIVE_CONE = "trigeo-live-cone-lyr"

@Composable
fun OutingMap(
    readings: List<Reading>,
    cameraTarget: GeoPoint?,
    liveLocation: GeoPoint?,
    liveBearingDeg: Double?,
    liveUncertaintyDeg: Double,
    liveBidirectional: Boolean,
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

    LaunchedEffect(liveLocation, liveBearingDeg, liveUncertaintyDeg, liveBidirectional, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        pushLiveData(style, liveLocation, liveBearingDeg, liveUncertaintyDeg, liveBidirectional)
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
    style.addSource(GeoJsonSource(SRC_LIVE_CONE))
    style.addSource(GeoJsonSource(SRC_LIVE_LINE))
    style.addSource(GeoJsonSource(SRC_LIVE_POINT))

    style.addLayer(
        FillLayer(LYR_LIVE_CONE, SRC_LIVE_CONE).withProperties(
            fillColor("#F2C94C"),
            fillOpacity(0.12f),
        ),
    )
    style.addLayer(
        LineLayer(LYR_LIVE_LINE, SRC_LIVE_LINE).withProperties(
            lineColor("#1F4E79"),
            lineWidth(2.5f),
            lineOpacity(0.7f),
            lineDasharray(arrayOf(3f, 3f)),
        ),
    )
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
    style.addLayer(
        CircleLayer(LYR_LIVE_POINT, SRC_LIVE_POINT).withProperties(
            circleRadius(6f),
            circleColor("#F2994A"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(2f),
        ),
    )
}

private fun pushOverlayData(style: Style, readings: List<Reading>) {
    val visible = readings.filter { it.visible }

    val points = visible.map { r ->
        Feature.fromGeometry(Point.fromLngLat(r.point.longitude, r.point.latitude))
    }
    (style.getSource(SRC_POINTS) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(points))

    val lines = visible.flatMap { r ->
        buildBearingLines(r.point, r.bearing.centerDeg, r.bidirectional)
    }
    (style.getSource(SRC_LINES) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(lines))

    val cones = visible.flatMap { r ->
        buildConePolygons(r.point, r.bearing.centerDeg, r.bearing.halfWidthDeg, r.bidirectional)
    }
    (style.getSource(SRC_CONES) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(cones))
}

private fun pushLiveData(
    style: Style,
    location: GeoPoint?,
    bearingDeg: Double?,
    uncertaintyDeg: Double,
    bidirectional: Boolean,
) {
    val pointSource = style.getSource(SRC_LIVE_POINT) as? GeoJsonSource
    val lineSource = style.getSource(SRC_LIVE_LINE) as? GeoJsonSource
    val coneSource = style.getSource(SRC_LIVE_CONE) as? GeoJsonSource

    if (location == null) {
        pointSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        lineSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        coneSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }

    pointSource?.setGeoJson(
        FeatureCollection.fromFeatures(
            listOf(Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude))),
        ),
    )

    if (bearingDeg == null) {
        lineSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        coneSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }

    val lines = buildBearingLines(location, bearingDeg, bidirectional)
    lineSource?.setGeoJson(FeatureCollection.fromFeatures(lines))

    val cones = buildConePolygons(location, bearingDeg, uncertaintyDeg / 2.0, bidirectional)
    coneSource?.setGeoJson(FeatureCollection.fromFeatures(cones))
}

private fun buildBearingLines(
    origin: GeoPoint,
    centerDeg: Double,
    bidirectional: Boolean,
): List<Feature> {
    val originPt = Point.fromLngLat(origin.longitude, origin.latitude)
    val forwardEnd = Geodesy.destination(origin, centerDeg, Defaults.BEARING_LINE_METERS)
    val forward = Feature.fromGeometry(
        LineString.fromLngLats(
            listOf(originPt, Point.fromLngLat(forwardEnd.longitude, forwardEnd.latitude)),
        ),
    )
    if (!bidirectional) return listOf(forward)
    val backEnd = Geodesy.destination(
        origin,
        Angles.normalize(centerDeg + 180.0),
        Defaults.BEARING_LINE_METERS,
    )
    val backward = Feature.fromGeometry(
        LineString.fromLngLats(
            listOf(originPt, Point.fromLngLat(backEnd.longitude, backEnd.latitude)),
        ),
    )
    return listOf(forward, backward)
}

private fun buildConePolygons(
    origin: GeoPoint,
    centerDeg: Double,
    halfWidthDeg: Double,
    bidirectional: Boolean,
): List<Feature> {
    val forward = buildConePolygon(origin, centerDeg, halfWidthDeg)
    if (!bidirectional) return listOf(forward)
    val backward = buildConePolygon(origin, Angles.normalize(centerDeg + 180.0), halfWidthDeg)
    return listOf(forward, backward)
}

private fun buildConePolygon(origin: GeoPoint, centerDeg: Double, halfWidthDeg: Double): Feature {
    val steps = 12
    val ring = mutableListOf(Point.fromLngLat(origin.longitude, origin.latitude))
    val start = Angles.normalize(centerDeg - halfWidthDeg)
    val span = halfWidthDeg * 2.0
    for (i in 0..steps) {
        val deg = start + span * (i.toDouble() / steps)
        val e = Geodesy.destination(origin, deg, Defaults.BEARING_LINE_METERS)
        ring.add(Point.fromLngLat(e.longitude, e.latitude))
    }
    ring.add(Point.fromLngLat(origin.longitude, origin.latitude))
    return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
}
