package ch.swimpoint.wear.app.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "AppSensorManager"

class AppSensorManager(
    private val context: Context,
    // Callback, das aufgerufen wird, wenn strokeCount sich ändert
    private val onStrokeCountChanged: (Int) -> Unit,
    // Callback, wenn eine relevante Kursabweichung erkannt wurde
    private val onCourseDeviation: (Boolean) -> Unit = {}
) : SensorEventListener {

    companion object {
        // Sampling-Raten
        private const val SAMPLING_RATE_IDLE = SensorManager.SENSOR_DELAY_NORMAL
        private const val SAMPLING_RATE_ACTIVE = SensorManager.SENSOR_DELAY_GAME

        // Bewegungsschwellwert für das Umschalten Idle -> Active
        private const val MOTION_THRESHOLD = 1.5f

        // Zeit ohne größere Bewegung, um von Active -> Idle zu wechseln
        private const val IDLE_TIMEOUT_MS = 3000L

        // Stroke Detection
        private const val MIN_TIME_BETWEEN_PEAKS_MS = 300L
        private const val ACC_THRESHOLD = 2.5f

        // Kursabweichung
        private const val YAW_CHANGE_THRESHOLD = 20f
        private const val DEVIATION_COUNT_THRESHOLD = 3
    }

    // SensorManager und Sensoren
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAcc: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Motion idle/active
    private var currentMode = SensorMode.IDLE
    private var lastSignificantMotionTime = System.currentTimeMillis()

    // Flag für Aufzeichnung
    private var isRecording = false

    // Zählung der Schwimmzüge
    private var strokeCount = 0
    private var isStrokeInProgress = false
    private var lastPeakTime = 0L

    // Gyro/Yaw
    private var initialYaw = 0f
    private var currentYaw = 0f
    private var lastGyroTimestamp = 0L
    private var initialYawSet = false
    private var deviationCounter = 0

    private fun magnitude(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360f
        if (a < 0f) a += 360f
        return a
    }

    private fun yawDifference(reference: Float, current: Float): Float {
        val diff = abs(current - reference)
        return if (diff > 180f) 360f - diff else diff
    }

    // -----------------------------------------------------------
    // Start/Stop Recording
    // -----------------------------------------------------------
    fun startRecording() {
        isRecording = true
        initialYawSet = false
        deviationCounter = 0
        // Registriere Sensoren zunächst im IDLE-Modus:
        registerSensors(SensorMode.IDLE)
        Log.d(TAG, "=== START Recording ===")
    }

    fun stopRecording() {
        isRecording = false
        // Sensoren abmelden
        unregisterSensors()
        onCourseDeviation(false)
        Log.d(TAG, "=== STOP Recording ===")
    }

    // -----------------------------------------------------------
    // Dynamische Registrierung
    // -----------------------------------------------------------
    private fun registerSensors(mode: SensorMode) {
        val rate = when (mode) {
            SensorMode.IDLE   -> SAMPLING_RATE_IDLE
            SensorMode.ACTIVE -> SAMPLING_RATE_ACTIVE
        }

        linearAcc?.let {
            sensorManager.registerListener(this, it, rate)
        } ?: Log.w(TAG, "TYPE_LINEAR_ACCELERATION nicht verfügbar")

        gyroscope?.let {
            sensorManager.registerListener(this, it, rate)
        } ?: Log.w(TAG, "TYPE_GYROSCOPE nicht verfügbar")

        rotationSensor?.let {
            sensorManager.registerListener(this, it, rate)
        } ?: Log.w(TAG, "TYPE_ROTATION_VECTOR nicht verfügbar")

        currentMode = mode
        Log.d(TAG, "Sensoren registriert mit Modus=$mode und Rate=$rate")
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Sensoren abgemeldet.")
    }

    private fun reRegisterSensors(newMode: SensorMode) {
        if (currentMode == newMode) return
        unregisterSensors()
        registerSensors(newMode)
    }

    // -----------------------------------------------------------
    // SensorEventListener
    // -----------------------------------------------------------
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> handleLinearAcceleration(event)
            Sensor.TYPE_GYROSCOPE          -> handleGyroscope(event)
            Sensor.TYPE_ROTATION_VECTOR    -> handleRotationVector(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // optional
    }

    // -----------------------------------------------------------
    // Dynamischer Wechsel IDLE <-> ACTIVE
    // -----------------------------------------------------------
    private fun checkForModeSwitch(magnitude: Float) {
        val now = System.currentTimeMillis()

        if (magnitude > MOTION_THRESHOLD) {
            // Größere Bewegung erkannt
            lastSignificantMotionTime = now
            if (currentMode == SensorMode.IDLE) {
                // Wechsle in ACTIVE-Modus
                reRegisterSensors(SensorMode.ACTIVE)
            }
        } else {
            // Keine große Bewegung
            if (currentMode == SensorMode.ACTIVE) {
                // Prüfe, ob wir schon lange (>= IDLE_TIMEOUT_MS)
                // keine signifikante Bewegung mehr hatten
                if (now - lastSignificantMotionTime > IDLE_TIMEOUT_MS) {
                    // Wechsel in IDLE-Modus
                    reRegisterSensors(SensorMode.IDLE)
                }
            }
        }
    }

    // -----------------------------------------------------------
    // LINEAR_ACCELERATION => Bewegung & Stroke-Detection
    // -----------------------------------------------------------
    private fun handleLinearAcceleration(event: SensorEvent) {
        val magnitude = magnitude(
            event.values[0],
            event.values[1],
            event.values[2]
        )

        // 1) Mode Switch prüfen
        checkForModeSwitch(magnitude)

        // 2) Wenn Active & Recording => Schwimmzüge erkennen
        if (currentMode == SensorMode.ACTIVE && isRecording) {
            detectStrokes(magnitude)
        }
    }

    private fun detectStrokes(magnitude: Float) {
        // Einfaches Beispiel: Peak-Detektion
        if (magnitude > ACC_THRESHOLD && !isStrokeInProgress) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPeakTime > MIN_TIME_BETWEEN_PEAKS_MS) {
                strokeCount++
                isStrokeInProgress = true
                lastPeakTime = currentTime

                Log.d(TAG, "Stroke erkannt! strokeCount = $strokeCount")
                onStrokeCountChanged(strokeCount)

                // Beispiel: alle 7 Züge => Richtung checken
                if (strokeCount == 7) {
                    checkOrientation()
                    strokeCount = 0
                    onStrokeCountChanged(strokeCount) // Zurück auf 0
                }
            }
        } else if (magnitude < ACC_THRESHOLD && isStrokeInProgress) {
            // Wieder unterhalb der Schwelle => bereit für neuen "Peak"
            isStrokeInProgress = false
        }
    }

    // -----------------------------------------------------------
    // GYROSCOPE => einfache Yaw-Berechnung (optional)
    // -----------------------------------------------------------
    private fun handleGyroscope(event: SensorEvent) {
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

    private fun handleRotationVector(event: SensorEvent) {
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

    private fun checkOrientation() {
        if (!initialYawSet) {
            Log.d(TAG, "Keine Initialorientierung gesetzt!")
            return
        }
        val diff = yawDifference(initialYaw, currentYaw)
        if (diff > YAW_CHANGE_THRESHOLD) {
            deviationCounter++
            Log.d(TAG, "Richtung stark geändert! (diff=$diff°)")
            if (deviationCounter >= DEVIATION_COUNT_THRESHOLD) {
                onCourseDeviation(true)
            }
        } else {
            if (deviationCounter > 0) {
                deviationCounter = 0
                onCourseDeviation(false)
            }
            Log.d(TAG, "Richtung weitgehend gleich. (diff=$diff°)")
        }
    }
}
