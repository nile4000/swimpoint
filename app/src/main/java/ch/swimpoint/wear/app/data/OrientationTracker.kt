package ch.swimpoint.wear.app.data

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

class OrientationTracker(
    private val onCourseDeviation: (Boolean) -> Unit,
    private val yawChangeThreshold: Float = 20f,
    private val deviationCountThreshold: Int = 3,
    private val tag: String = "OrientationTracker"
) {
    private var initialYaw = 0f
    private var currentYaw = 0f
    private var lastGyroTimestamp = 0L
    private var initialYawSet = false
    private var deviationCounter = 0

    fun reset() {
        initialYawSet = false
        deviationCounter = 0
        lastGyroTimestamp = 0L
    }

    fun onGyroscope(event: SensorEvent) {
        val gyroZ = event.values[2]
        val timestamp = event.timestamp

        if (lastGyroTimestamp != 0L) {
            val dt = (timestamp - lastGyroTimestamp) * 1e-9f
            val deltaYawDeg = gyroZ * dt * (180f / Math.PI.toFloat())

            currentYaw = normalizeAngle(currentYaw + deltaYawDeg)
            if (!initialYawSet) {
                initialYaw = currentYaw
                initialYawSet = true
            }
        }
        lastGyroTimestamp = timestamp
    }

    fun onRotationVector(event: SensorEvent) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        // azimuth in radians -> convert to degrees
        val yaw = normalizeAngle(Math.toDegrees(orientation[0].toDouble()).toFloat())

        currentYaw = yaw
        if (!initialYawSet) {
            initialYaw = currentYaw
            initialYawSet = true
        }
    }

    fun checkOrientation() {
        if (!initialYawSet) {
            Log.d(tag, "Keine Initialorientierung gesetzt!")
            return
        }
        val diff = yawDifference(initialYaw, currentYaw)
        if (diff > yawChangeThreshold) {
            deviationCounter++
            Log.d(tag, "Richtung stark geändert! (diff=$diff°)")
            if (deviationCounter >= deviationCountThreshold) {
                onCourseDeviation(true)
            }
        } else {
            if (deviationCounter > 0) {
                deviationCounter = 0
                onCourseDeviation(false)
            }
            Log.d(tag, "Richtung weitgehend gleich. (diff=$diff°)")
        }
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360f
        if (a < 0f) a += 360f
        return a
    }

    private fun yawDifference(reference: Float, current: Float): Float {
        val diff = abs(current - reference)
        return if (diff > 180f) 360f - diff else diff
    }
}
