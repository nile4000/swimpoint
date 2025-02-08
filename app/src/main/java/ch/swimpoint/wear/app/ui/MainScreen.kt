package ch.swimpoint.wear.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ch.swimpoint.wear.app.R
import com.example.swimapp.data.AppSensorManager

class MainScreen : AppCompatActivity() {

    private lateinit var appSensorManager: AppSensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        // Manager initialize
        appSensorManager = AppSensorManager(this)

        val startButton: Button = findViewById(R.id.start_button)
        startButton.setOnClickListener {
            Toast.makeText(this, "Aufzeichnung gestartet!", Toast.LENGTH_SHORT).show()

            // Sensors
            appSensorManager.startListening()
            appSensorManager.startRecording()
        }
        val endButton: Button = findViewById(R.id.end_button)
        endButton.setOnClickListener {
            Toast.makeText(this, "Ende!", Toast.LENGTH_SHORT).show()

            // Sensors
            appSensorManager.stopRecording()
            appSensorManager.stopListening()
        }
    }
}
