package com.example.swimapp.data

import android.content.Context
import android.hardware.*
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

class AppSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Nur diese Zeile behalten, falls du LINEAR_ACCELERATION statt ACCELEROMETER verwenden willst
    private val linearAcc: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Flag für Aufzeichnung
    private var isRecording = false

    // Zählung der Schwimmzüge
    private var strokeCount = 0
    private var isStrokeInProgress = false
    private var lastPeakTime = 0L
    private val MIN_TIME_BETWEEN_PEAKS_MS = 300L

    // Gyro/Yaw
    private var initialYaw = 0f
    private var currentYaw = 0f
    private var lastGyroTimestamp = 0L
    private var initialYawSet = false

    // Schwellenwerte
    private val ACC_THRESHOLD = 2.5f        // z.B. 2.5 oder 3.0
    private val YAW_CHANGE_THRESHOLD = 20f

    fun startListening() {
        linearAcc?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: Log.w("AppSensorManager", "TYPE_LINEAR_ACCELERATION nicht verfügbar")

        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    fun startRecording() {
        isRecording = true
        Log.d("AppSensorManager", "=== START Recording ===")
    }

    fun stopRecording() {
        isRecording = false
        Log.d("AppSensorManager", "=== STOP Recording ===")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> handleLinearAcceleration(event)
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
        }

        // Falls Recording aktiv => Werte loggen
        if (isRecording) {
            val (x, y, z) = event.values
            Log.d(
                "AppSensorManager",
                "Sensor=${event.sensor.name} | x=$x, y=$y, z=$z (t=${event.timestamp})"
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // optional
    }

    // ---------------------------------------------------------
    // Züge erkennen über LINEAR_ACCELERATION (ohne Gravitation)
    // ---------------------------------------------------------
    private fun handleLinearAcceleration(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Hier liegt der Ruhewert nahe 0 (ohne Gravitation)
        val magnitude = sqrt(x*x + y*y + z*z)

        // Logge optional zur Analyse
        if (isRecording) {
            Log.d("AppSensorManager", "magnitude=$magnitude (t=${event.timestamp})")
        }

        if (magnitude > ACC_THRESHOLD && !isStrokeInProgress) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPeakTime > MIN_TIME_BETWEEN_PEAKS_MS) {
                strokeCount++
                isStrokeInProgress = true
                lastPeakTime = currentTime

                Log.d("AppSensorManager", "Stroke erkannt! strokeCount = $strokeCount")

                // Beispiel: alle 7 Züge => Richtung prüfen
                if (strokeCount == 7) {
                    checkOrientation()
                    strokeCount = 0
                }
            }
        } else if (magnitude < ACC_THRESHOLD && isStrokeInProgress) {
            // Zurück unter die Schwelle => bereit für nächsten "Peak"
            isStrokeInProgress = false
        }
    }

    // ---------------------------------------------------------
    // Gyroskop => einfache Yaw-Berechnung (optional)
    // ---------------------------------------------------------
    private fun handleGyroscope(event: SensorEvent) {
        val gyroZ = event.values[2]
        val timestamp = event.timestamp

        if (lastGyroTimestamp != 0L) {
            val dt = (timestamp - lastGyroTimestamp) * 1e-9f
            val deltaYawDeg = gyroZ * dt * (180f / Math.PI.toFloat())

            currentYaw += deltaYawDeg
            if (currentYaw < 0) currentYaw += 360f
            if (currentYaw > 360f) currentYaw -= 360f

            if (!initialYawSet) {
                initialYaw = currentYaw
                initialYawSet = true
            }
        }
        lastGyroTimestamp = timestamp
    }

    private fun checkOrientation() {
        if (!initialYawSet) {
            Log.d("AppSensorManager", "Keine Initialorientierung gesetzt!")
            return
        }
        val diff = abs(currentYaw - initialYaw)
        val normalizedDiff = if (diff > 180) 360 - diff else diff

        if (normalizedDiff > YAW_CHANGE_THRESHOLD) {
            Log.d("AppSensorManager", "Richtung stark geändert! (diff=$normalizedDiff°)")
        } else {
            Log.d("AppSensorManager", "Richtung weitgehend gleich. (diff=$normalizedDiff°)")
        }
    }
}
