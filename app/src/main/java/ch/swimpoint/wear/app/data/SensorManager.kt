package ch.swimpoint.wear.app.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
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

    // Delegated helpers
    private val orientationTracker = OrientationTracker(
        onCourseDeviation = onCourseDeviation,
        yawChangeThreshold = YAW_CHANGE_THRESHOLD,
        deviationCountThreshold = DEVIATION_COUNT_THRESHOLD,
        tag = TAG
    )

    private val strokeDetector = StrokeDetector(
        onStrokeCountChanged = onStrokeCountChanged,
        onCycleCompleted = { orientationTracker.checkOrientation() },
        strokesPerCycle = 7,
        accThreshold = ACC_THRESHOLD,
        minTimeBetweenPeaksMs = MIN_TIME_BETWEEN_PEAKS_MS,
        tag = TAG
    )

    private fun magnitude(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)


    // -----------------------------------------------------------
    // Start/Stop Recording
    // -----------------------------------------------------------
    fun startRecording() {
        isRecording = true
        strokeDetector.reset()
        orientationTracker.reset()
        // Registriere Sensoren zunächst im IDLE-Modus:
        registerSensors(SensorMode.IDLE)
        Log.d(TAG, "=== START Recording ===")
    }

    fun stopRecording() {
        isRecording = false
        unregisterSensors()
        orientationTracker.reset()
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
            strokeDetector.process(magnitude)
        }
    }

    // -----------------------------------------------------------
    // GYROSCOPE => einfache Yaw-Berechnung (optional)
    // -----------------------------------------------------------
    private fun handleGyroscope(event: SensorEvent) {
        orientationTracker.onGyroscope(event)
    }

    private fun handleRotationVector(event: SensorEvent) {
        orientationTracker.onRotationVector(event)
    }
}
