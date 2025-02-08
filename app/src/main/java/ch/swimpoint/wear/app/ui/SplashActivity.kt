package ch.swimpoint.wear.app.ui
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import ch.swimpoint.wear.app.R

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainScreen::class.java)
            startActivity(intent)
            finish()
        }, 2000)
    }
}

