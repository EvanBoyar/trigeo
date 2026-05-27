package com.trigeo.app.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.trigeo.app.geo.Angles
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compass reading with declination already applied.
 *
 * `trueDeg` is the magnetic heading rotated by the current GeomagneticField
 * declination, so it points at true north. `orientation` tells the UI which
 * convention is in use so it can prompt the user accordingly.
 */
data class CompassReading(
    val trueDeg: Double,
    val magneticDeg: Double,
    val declinationDeg: Double,
    val orientation: PhoneOrientation,
)

enum class PhoneOrientation {
    FLAT,             // screen up, top edge points at the target
    UPRIGHT_PORTRAIT, // screen vertical, back of phone points at the target
}

class CompassService(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    fun isAvailable(): Boolean = rotationSensor != null

    /**
     * Emits a smoothed CompassReading at roughly the sensor's UI rate.
     *
     *  - `latitudeProvider` / `longitudeProvider` are called on every event
     *    to look up the current declination. Pass providers that return the
     *    user's best known position, or 0.0/0.0 if unknown (declination will
     *    be wrong but the magnetic heading is still useful).
     *  - `altitudeMetersProvider` may return 0.0 if unknown.
     */
    fun headingUpdates(
        latitudeProvider: () -> Double,
        longitudeProvider: () -> Double,
        altitudeMetersProvider: () -> Double = { 0.0 },
        smoothing: Double = 0.15,
    ): Flow<CompassReading> = callbackFlow {
        val sensor = rotationSensor
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val R = FloatArray(9)
        val Rremap = FloatArray(9)
        val orientationOut = FloatArray(3)
        var smoothedSin = Double.NaN
        var smoothedCos = Double.NaN

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(R, event.values)
                SensorManager.getOrientation(R, orientationOut)
                val pitch = orientationOut[1]

                // Treat phone as upright-portrait when the screen is tilted
                // more than ~30 degrees away from horizontal AND pitched
                // toward "top edge up". Otherwise treat it as flat.
                val isUpright = pitch < -PI / 6.0
                val orientation = if (isUpright) {
                    PhoneOrientation.UPRIGHT_PORTRAIT
                } else {
                    PhoneOrientation.FLAT
                }

                val magneticDeg = if (orientation == PhoneOrientation.UPRIGHT_PORTRAIT) {
                    // The remap below leaves the new "device Y" axis aligned
                    // with the original device Z (screen normal, toward the
                    // user). The right-handed-system constraint inside
                    // remapCoordinateSystem prevents us from flipping that
                    // sign directly, so we add 180 degrees to get the back of
                    // the phone (toward the signal) instead.
                    SensorManager.remapCoordinateSystem(
                        R,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_MINUS_Z,
                        Rremap,
                    )
                    SensorManager.getOrientation(Rremap, orientationOut)
                    Angles.normalize(Math.toDegrees(orientationOut[0].toDouble()) + 180.0)
                } else {
                    Angles.normalize(Math.toDegrees(orientationOut[0].toDouble()))
                }

                val lat = latitudeProvider().toFloat()
                val lon = longitudeProvider().toFloat()
                val alt = altitudeMetersProvider().toFloat()
                val declinationDeg = try {
                    GeomagneticField(lat, lon, alt, System.currentTimeMillis()).declination.toDouble()
                } catch (_: IllegalArgumentException) {
                    0.0
                }
                val trueDeg = Angles.normalize(magneticDeg + declinationDeg)

                // Wrap-aware exponential smoothing on the unit circle.
                val s = sin(Math.toRadians(trueDeg))
                val c = cos(Math.toRadians(trueDeg))
                if (smoothedSin.isNaN()) {
                    smoothedSin = s
                    smoothedCos = c
                } else {
                    smoothedSin += smoothing * (s - smoothedSin)
                    smoothedCos += smoothing * (c - smoothedCos)
                }
                val smoothedDeg = Angles.normalize(Math.toDegrees(atan2(smoothedSin, smoothedCos)))

                trySend(
                    CompassReading(
                        trueDeg = smoothedDeg,
                        magneticDeg = magneticDeg,
                        declinationDeg = declinationDeg,
                        orientation = orientation,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
