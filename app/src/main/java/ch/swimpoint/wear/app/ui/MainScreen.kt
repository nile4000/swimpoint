package ch.swimpoint.wear.app.ui

import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import android.widget.ImageView
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.ambient.AmbientLifecycleObserver
import ch.swimpoint.wear.app.R
import ch.swimpoint.wear.app.data.AppSensorManager

class MainScreen : AppCompatActivity(), AmbientLifecycleObserver {

    private lateinit var appSensorManager: AppSensorManager
    private lateinit var ambientObserver: AmbientLifecycleObserver

    private lateinit var strokeCountTextView: TextView
    private lateinit var swimChronometer: Chronometer
    private lateinit var courseIndicator: ImageView
    private lateinit var vibrator: Vibrator


    override fun onCreate(savedInstanceState: Bundle?) {
        super<AppCompatActivity>.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ambientObserver = AmbientLifecycleObserver(this /* LifecycleOwner */, this /* Callback */)
        lifecycle.addObserver(ambientObserver)

        // TextView und Chronometer aus dem Layout holen
        strokeCountTextView = findViewById(R.id.strokeCountTextView)
        swimChronometer = findViewById(R.id.swimChronometer)
        courseIndicator = findViewById(R.id.courseIndicator)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        // SensorManager initialisieren und Callback definieren,
        // der bei Änderung die UI updatet.
        appSensorManager = AppSensorManager(
            context = this,
            onStrokeCountChanged = { newStrokeCount ->
                runOnUiThread {
                    strokeCountTextView.text = "$newStrokeCount"
                }
            },
            onCourseDeviation = { deviated ->
                runOnUiThread {
                    courseIndicator.visibility = if (deviated) ImageView.VISIBLE else ImageView.GONE
                    if (deviated) {
                        val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                        vibrator.vibrate(effect)
                    }
                }
            }
        )

        // Sensoren und Aufzeichnung automatisch starten,
        // sobald Activity erstellt wird.
        appSensorManager.startRecording()

        // Chronometer starten
        swimChronometer.base = SystemClock.elapsedRealtime()
        swimChronometer.start()

        // Falls du die Buttons noch nutzt:
        val startButton: Button = findViewById(R.id.start_button)
        startButton.setOnClickListener {
            Toast.makeText(this, "Aufzeichnung (erneut) gestartet!", Toast.LENGTH_SHORT).show()

            // Sensors
            appSensorManager.startRecording()

            // Chronometer neu starten
            swimChronometer.base = SystemClock.elapsedRealtime()
            swimChronometer.start()
        }

        val endButton: Button = findViewById(R.id.end_button)
        endButton.setOnClickListener {
            Toast.makeText(this, "Ende!", Toast.LENGTH_SHORT).show()

            // Sensors
            appSensorManager.stopRecording()

            // Chronometer anhalten
            swimChronometer.stop()
        }
    }

    override val isAmbient: Boolean
        get() = TODO("Not yet implemented")

}
