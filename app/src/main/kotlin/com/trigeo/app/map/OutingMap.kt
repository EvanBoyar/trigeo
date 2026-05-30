package com.trigeo.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.trigeo.app.domain.ReadingDirection
import com.trigeo.app.geo.Angles
import com.trigeo.app.geo.Geodesy
import com.trigeo.app.geo.TriangulationFix
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val SRC_POINTS = "trigeo-points"
private const val SRC_LINES_SOLID = "trigeo-lines-solid"
private const val SRC_LINES_PHANTOM = "trigeo-lines-phantom"
private const val SRC_CONES = "trigeo-cones"
private const val LYR_POINTS = "trigeo-points-lyr"
private const val LYR_LINES_SOLID = "trigeo-lines-solid-lyr"
private const val LYR_LINES_PHANTOM = "trigeo-lines-phantom-lyr"
private const val LYR_CONES = "trigeo-cones-lyr"
private const val IMG_READING_DOT = "trigeo-reading-dot"
private const val COLOR_CAPTURED_BEARING = "#000000"
private const val COLOR_LIVE_BEARING = "#EF4444"
private const val COLOR_PHANTOM_BEARING = "#374151"

private const val SRC_LIVE_POINT = "trigeo-live-point"
private const val SRC_LIVE_LINE = "trigeo-live-line"
private const val SRC_LIVE_LINE_PHANTOM = "trigeo-live-line-phantom"
private const val SRC_LIVE_CONE = "trigeo-live-cone"
private const val LYR_LIVE_POINT = "trigeo-live-point-lyr"
private const val LYR_LIVE_LINE = "trigeo-live-line-lyr"
private const val LYR_LIVE_LINE_PHANTOM = "trigeo-live-line-phantom-lyr"
private const val LYR_LIVE_CONE = "trigeo-live-cone-lyr"
private const val IMG_LIVE_DOT = "trigeo-live-dot"
private const val IMG_LIVE_DOT_ARROW = "trigeo-live-dot-arrow"
private const val PROP_BEARING = "bearing"
private const val PROP_ICON = "icon"

private const val SRC_FIX_POINT = "trigeo-fix-point"
private const val SRC_FIX_ELLIPSE = "trigeo-fix-ellipse"
private const val LYR_FIX_POINT = "trigeo-fix-point-lyr"
private const val LYR_FIX_ELLIPSE_FILL = "trigeo-fix-ellipse-fill-lyr"
private const val LYR_FIX_ELLIPSE_LINE = "trigeo-fix-ellipse-line-lyr"

private const val SRC_PENDING = "trigeo-pending-point"
private const val LYR_PENDING = "trigeo-pending-point-lyr"

private const val SRC_GPS_ACCURACY = "trigeo-gps-accuracy"
private const val LYR_GPS_ACCURACY = "trigeo-gps-accuracy-lyr"

private const val ELLIPSE_SIGMA = 2.0

data class CameraRequest(val point: GeoPoint, val token: Long = System.nanoTime())

class MapBoundsHolder {
    @Volatile private var ref: MapLibreMap? = null
    fun attach(map: MapLibreMap?) { ref = map }
    fun visibleBounds(): org.maplibre.android.geometry.LatLngBounds? =
        ref?.projection?.visibleRegion?.latLngBounds
}

@Composable
fun OutingMap(
    readings: List<Reading>,
    cameraRequest: CameraRequest?,
    liveLocation: GeoPoint?,
    liveAccuracyMeters: Float?,
    liveBearingDeg: Double?,
    liveUncertaintyDeg: Double,
    liveDirection: ReadingDirection,
    tileStyle: MapTileStyle,
    fix: TriangulationFix?,
    pendingPoint: GeoPoint?,
    bearingDeg: Double,
    rotationEnabled: Boolean,
    boundsHolder: MapBoundsHolder,
    onLongPress: (GeoPoint) -> Unit,
    cameraZoom: Double = 14.0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentLongPress by rememberUpdatedState(onLongPress)

    var styleRef by remember { mutableStateOf<Style?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var recenterToken by remember { mutableStateOf<Long?>(null) }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            getMapAsync { map ->
                mapRef = map
                boundsHolder.attach(map)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(20.0, 0.0))
                    .zoom(1.5)
                    .build()
                map.addOnMapLongClickListener { p ->
                    currentLongPress(GeoPoint(p.latitude, p.longitude))
                    true
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

    LaunchedEffect(tileStyle, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        styleRef = null
        map.setStyle(Style.Builder().fromUri(tileStyle.styleUri(context))) { style ->
            val liveFill = 0xFFF2994A.toInt()
            val readingFill = 0xFF1F4E79.toInt()
            val whiteStroke = 0xFFFFFFFF.toInt()
            style.addImage(
                IMG_LIVE_DOT,
                makeDotBitmap(
                    context,
                    fillColor = liveFill,
                    strokeColor = whiteStroke,
                    dotRadiusDp = 6f,
                    strokeWidthDp = 1.5f,
                ),
            )
            style.addImage(
                IMG_LIVE_DOT_ARROW,
                makeDotBitmap(
                    context,
                    fillColor = liveFill,
                    strokeColor = whiteStroke,
                    dotRadiusDp = 6f,
                    strokeWidthDp = 1.5f,
                    arrowHeightDp = 12f,
                    arrowBaseDp = 11f,
                    arrowOverlapDp = 3f,
                ),
            )
            style.addImage(
                IMG_READING_DOT,
                makeDotBitmap(
                    context,
                    fillColor = readingFill,
                    strokeColor = whiteStroke,
                    dotRadiusDp = 7f,
                    strokeWidthDp = 2f,
                ),
            )
            addOverlayLayers(style)
            styleRef = style
        }
    }

    LaunchedEffect(readings, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        pushOverlayData(style, readings)
    }

    LaunchedEffect(liveLocation, liveBearingDeg, liveUncertaintyDeg, liveDirection, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        pushLiveData(style, liveLocation, liveBearingDeg, liveUncertaintyDeg, liveDirection)
    }

    LaunchedEffect(liveLocation, liveAccuracyMeters, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        pushGpsAccuracyData(style, liveLocation, liveAccuracyMeters)
    }

    LaunchedEffect(fix, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        pushFixData(style, fix)
    }

    LaunchedEffect(pendingPoint, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        pushPendingData(style, pendingPoint)
    }

    LaunchedEffect(cameraRequest, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        val req = cameraRequest ?: return@LaunchedEffect
        val myToken = req.token
        recenterToken = myToken
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(req.point.latitude, req.point.longitude),
                cameraZoom,
            ),
            object : MapLibreMap.CancelableCallback {
                override fun onCancel() {
                    if (recenterToken == myToken) recenterToken = null
                }
                override fun onFinish() {
                    if (recenterToken == myToken) recenterToken = null
                }
            },
        )
    }

    LaunchedEffect(bearingDeg, mapRef, recenterToken) {
        val map = mapRef ?: return@LaunchedEffect
        if (recenterToken != null) return@LaunchedEffect
        val current = map.cameraPosition
        val next = CameraPosition.Builder(current).bearing(bearingDeg).build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(next), 200)
    }

    LaunchedEffect(rotationEnabled, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        map.uiSettings.isRotateGesturesEnabled = rotationEnabled
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addOverlayLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_CONES))
    style.addSource(GeoJsonSource(SRC_LINES_SOLID))
    style.addSource(GeoJsonSource(SRC_LINES_PHANTOM))
    style.addSource(GeoJsonSource(SRC_POINTS))
    style.addSource(GeoJsonSource(SRC_LIVE_CONE))
    style.addSource(GeoJsonSource(SRC_LIVE_LINE))
    style.addSource(GeoJsonSource(SRC_LIVE_LINE_PHANTOM))
    style.addSource(GeoJsonSource(SRC_LIVE_POINT))
    style.addSource(GeoJsonSource(SRC_FIX_ELLIPSE))
    style.addSource(GeoJsonSource(SRC_FIX_POINT))
    style.addSource(GeoJsonSource(SRC_PENDING))
    style.addSource(GeoJsonSource(SRC_GPS_ACCURACY))

    style.addLayer(
        FillLayer(LYR_GPS_ACCURACY, SRC_GPS_ACCURACY).withProperties(
            fillColor("#1F4E79"),
            fillOpacity(0.10f),
        ),
    )
    style.addLayer(
        FillLayer(LYR_LIVE_CONE, SRC_LIVE_CONE).withProperties(
            fillColor("#F2C94C"),
            fillOpacity(0.12f),
        ),
    )
    style.addLayer(
        LineLayer(LYR_LIVE_LINE_PHANTOM, SRC_LIVE_LINE_PHANTOM).withProperties(
            lineColor(COLOR_PHANTOM_BEARING),
            lineWidth(3f),
            lineOpacity(0.7f),
            lineDasharray(arrayOf(3f, 3f)),
        ),
    )
    style.addLayer(
        LineLayer(LYR_LIVE_LINE, SRC_LIVE_LINE).withProperties(
            lineColor(COLOR_LIVE_BEARING),
            lineWidth(3f),
            lineOpacity(0.85f),
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
        LineLayer(LYR_LINES_PHANTOM, SRC_LINES_PHANTOM).withProperties(
            lineColor(COLOR_PHANTOM_BEARING),
            lineWidth(3.5f),
            lineOpacity(0.75f),
            lineDasharray(arrayOf(2.5f, 2.5f)),
        ),
    )
    style.addLayer(
        LineLayer(LYR_LINES_SOLID, SRC_LINES_SOLID).withProperties(
            lineColor(COLOR_CAPTURED_BEARING),
            lineWidth(3.5f),
            lineOpacity(0.95f),
        ),
    )
    style.addLayer(
        FillLayer(LYR_FIX_ELLIPSE_FILL, SRC_FIX_ELLIPSE).withProperties(
            fillColor("#EF4444"),
            fillOpacity(0.18f),
        ),
    )
    style.addLayer(
        LineLayer(LYR_FIX_ELLIPSE_LINE, SRC_FIX_ELLIPSE).withProperties(
            lineColor("#EF4444"),
            lineWidth(2f),
            lineOpacity(0.9f),
        ),
    )
    style.addLayer(
        SymbolLayer(LYR_POINTS, SRC_POINTS).withProperties(
            iconImage(Expression.get(PROP_ICON)),
            iconRotate(Expression.get(PROP_BEARING)),
            iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        ),
    )
    style.addLayer(
        SymbolLayer(LYR_LIVE_POINT, SRC_LIVE_POINT).withProperties(
            iconImage(Expression.get(PROP_ICON)),
            iconRotate(Expression.get(PROP_BEARING)),
            iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        ),
    )
    style.addLayer(
        CircleLayer(LYR_FIX_POINT, SRC_FIX_POINT).withProperties(
            circleRadius(9f),
            circleColor("#EF4444"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(3f),
        ),
    )
    style.addLayer(
        CircleLayer(LYR_PENDING, SRC_PENDING).withProperties(
            circleRadius(9f),
            circleColor("#F2C94C"),
            circleStrokeColor("#1E293B"),
            circleStrokeWidth(3f),
        ),
    )
}

private fun pushGpsAccuracyData(
    style: Style,
    location: GeoPoint?,
    accuracyMeters: Float?,
) {
    val src = style.getSource(SRC_GPS_ACCURACY) as? GeoJsonSource ?: return
    if (location == null || accuracyMeters == null || accuracyMeters <= 0f) {
        src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val steps = 48
    val ring = mutableListOf<Point>()
    for (i in 0..steps) {
        val bearing = 360.0 * (i.toDouble() / steps)
        val v = Geodesy.destination(location, bearing, accuracyMeters.toDouble())
        ring.add(Point.fromLngLat(v.longitude, v.latitude))
    }
    src.setGeoJson(
        FeatureCollection.fromFeatures(
            listOf(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))),
        ),
    )
}

private fun pushPendingData(style: Style, point: GeoPoint?) {
    val src = style.getSource(SRC_PENDING) as? GeoJsonSource ?: return
    if (point == null) {
        src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    } else {
        src.setGeoJson(
            FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))),
            ),
        )
    }
}

private fun pushFixData(style: Style, fix: TriangulationFix?) {
    val pointSource = style.getSource(SRC_FIX_POINT) as? GeoJsonSource
    val ellipseSource = style.getSource(SRC_FIX_ELLIPSE) as? GeoJsonSource
    if (fix == null) {
        pointSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        ellipseSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    pointSource?.setGeoJson(
        FeatureCollection.fromFeatures(
            listOf(
                Feature.fromGeometry(Point.fromLngLat(fix.point.longitude, fix.point.latitude)),
            ),
        ),
    )
    ellipseSource?.setGeoJson(
        FeatureCollection.fromFeatures(listOf(buildEllipsePolygon(fix))),
    )
}

private fun buildEllipsePolygon(fix: TriangulationFix): Feature {
    val steps = 64
    val a = fix.errorEllipse.semiMajorMeters * ELLIPSE_SIGMA
    val b = fix.errorEllipse.semiMinorMeters * ELLIPSE_SIGMA
    val orientRad = Math.toRadians(fix.errorEllipse.orientationDeg)
    val majorEast = kotlin.math.sin(orientRad)
    val majorNorth = kotlin.math.cos(orientRad)
    val minorEast = kotlin.math.cos(orientRad)
    val minorNorth = -kotlin.math.sin(orientRad)

    val ring = mutableListOf<Point>()
    for (i in 0..steps) {
        val t = 2.0 * Math.PI * (i.toDouble() / steps)
        val u = a * kotlin.math.cos(t)
        val v = b * kotlin.math.sin(t)
        val east = u * majorEast + v * minorEast
        val north = u * majorNorth + v * minorNorth
        val distance = kotlin.math.hypot(east, north)
        if (distance < 1e-6) {
            ring.add(Point.fromLngLat(fix.point.longitude, fix.point.latitude))
            continue
        }
        val bearingDeg = Angles.normalize(Math.toDegrees(kotlin.math.atan2(east, north)))
        val vertex = Geodesy.destination(fix.point, bearingDeg, distance)
        ring.add(Point.fromLngLat(vertex.longitude, vertex.latitude))
    }
    return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
}

private fun pushOverlayData(style: Style, readings: List<Reading>) {
    val visible = readings.filter { it.visible }

    val points = visible.map { r ->
        Feature.fromGeometry(
            Point.fromLngLat(r.point.longitude, r.point.latitude),
        ).apply {
            addStringProperty(PROP_ICON, IMG_READING_DOT)
            addNumberProperty(PROP_BEARING, r.bearing.centerDeg)
        }
    }
    (style.getSource(SRC_POINTS) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(points))

    val actualLines = mutableListOf<Feature>()
    val phantomLines = mutableListOf<Feature>()
    for (r in visible) {
        actualLines += buildBearingLines(r.point, r.bearing.centerDeg, r.direction)
        if (r.direction == ReadingDirection.REVERSED) {
            phantomLines += buildBearingLines(r.point, r.bearing.centerDeg, ReadingDirection.NORMAL)
        }
    }
    (style.getSource(SRC_LINES_SOLID) as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(actualLines))
    (style.getSource(SRC_LINES_PHANTOM) as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(phantomLines))

    val cones = visible.flatMap { r ->
        buildConePolygons(r.point, r.bearing.centerDeg, r.bearing.halfWidthDeg, r.direction)
    }
    (style.getSource(SRC_CONES) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(cones))
}

private fun pushLiveData(
    style: Style,
    location: GeoPoint?,
    bearingDeg: Double?,
    uncertaintyDeg: Double,
    direction: ReadingDirection,
) {
    val pointSource = style.getSource(SRC_LIVE_POINT) as? GeoJsonSource
    val lineSource = style.getSource(SRC_LIVE_LINE) as? GeoJsonSource
    val phantomLineSource = style.getSource(SRC_LIVE_LINE_PHANTOM) as? GeoJsonSource
    val coneSource = style.getSource(SRC_LIVE_CONE) as? GeoJsonSource

    if (location == null) {
        pointSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        lineSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        phantomLineSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        coneSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }

    val livePointIcon = if (bearingDeg != null) IMG_LIVE_DOT_ARROW else IMG_LIVE_DOT
    val livePointFeature = Feature.fromGeometry(
        Point.fromLngLat(location.longitude, location.latitude),
    ).apply {
        addStringProperty(PROP_ICON, livePointIcon)
        addNumberProperty(PROP_BEARING, bearingDeg ?: 0.0)
    }
    pointSource?.setGeoJson(FeatureCollection.fromFeatures(listOf(livePointFeature)))

    if (bearingDeg == null) {
        lineSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        phantomLineSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        coneSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }

    val actualLines = buildBearingLines(location, bearingDeg, direction)
    lineSource?.setGeoJson(FeatureCollection.fromFeatures(actualLines))
    val phantomLines = if (direction == ReadingDirection.REVERSED) {
        buildBearingLines(location, bearingDeg, ReadingDirection.NORMAL)
    } else {
        emptyList()
    }
    phantomLineSource?.setGeoJson(FeatureCollection.fromFeatures(phantomLines))

    val cones = buildConePolygons(location, bearingDeg, uncertaintyDeg / 2.0, direction)
    coneSource?.setGeoJson(FeatureCollection.fromFeatures(cones))
}

private fun makeDotBitmap(
    context: Context,
    fillColor: Int,
    strokeColor: Int,
    dotRadiusDp: Float,
    strokeWidthDp: Float,
    arrowHeightDp: Float = 0f,
    arrowBaseDp: Float = 0f,
    arrowOverlapDp: Float = 0f,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val r = dotRadiusDp * density
    val strokePx = strokeWidthDp * density
    val arrowH = arrowHeightDp * density
    val arrowBase = arrowBaseDp * density
    val arrowOverlap = arrowOverlapDp * density
    val hasArrow = arrowHeightDp > 0f && arrowBaseDp > 0f

    val halfStroke = strokePx / 2f
    val extentAbove = if (hasArrow) r - arrowOverlap + arrowH + halfStroke else r + halfStroke
    val extentBelow = r + halfStroke
    val verticalHalf = maxOf(extentAbove, extentBelow)
    val halfWidth = maxOf(r, arrowBase / 2f) + halfStroke

    val w = (2f * halfWidth).toInt().coerceAtLeast(1)
    val h = (2f * verticalHalf).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = w / 2f
    val cy = h / 2f

    val combined = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
    if (hasArrow) {
        val baseY = cy - r + arrowOverlap
        val apexY = baseY - arrowH
        val triangle = Path().apply {
            moveTo(cx, apexY)
            lineTo(cx + arrowBase / 2f, baseY)
            lineTo(cx - arrowBase / 2f, baseY)
            close()
        }
        combined.op(triangle, Path.Op.UNION)
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(combined, fillPaint)
    canvas.drawPath(combined, strokePaint)
    return bmp
}

private fun effectiveBearings(centerDeg: Double, direction: ReadingDirection): List<Double> =
    when (direction) {
        ReadingDirection.NORMAL -> listOf(Angles.normalize(centerDeg))
        ReadingDirection.REVERSED -> listOf(Angles.normalize(centerDeg + 180.0))
        ReadingDirection.BIDIRECTIONAL ->
            listOf(Angles.normalize(centerDeg), Angles.normalize(centerDeg + 180.0))
    }

private fun buildBearingLines(
    origin: GeoPoint,
    centerDeg: Double,
    direction: ReadingDirection,
): List<Feature> {
    val originPt = Point.fromLngLat(origin.longitude, origin.latitude)
    return effectiveBearings(centerDeg, direction).map { deg ->
        val end = Geodesy.destination(origin, deg, Defaults.BEARING_LINE_METERS)
        Feature.fromGeometry(
            LineString.fromLngLats(
                listOf(originPt, Point.fromLngLat(end.longitude, end.latitude)),
            ),
        )
    }
}

private fun buildConePolygons(
    origin: GeoPoint,
    centerDeg: Double,
    halfWidthDeg: Double,
    direction: ReadingDirection,
): List<Feature> =
    effectiveBearings(centerDeg, direction).map { deg ->
        buildConePolygon(origin, deg, halfWidthDeg)
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
