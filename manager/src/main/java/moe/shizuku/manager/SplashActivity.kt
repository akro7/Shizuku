package moe.shizuku.manager

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val icon    = findViewById<ImageView>(R.id.splash_icon)
        val name    = findViewById<TextView>(R.id.splash_app_name)
        val tagline = findViewById<TextView>(R.id.splash_tagline)
        val dot1    = findViewById<View>(R.id.dot1)
        val dot2    = findViewById<View>(R.id.dot2)
        val dot3    = findViewById<View>(R.id.dot3)

        // Icon pop-in
        icon.scaleX = 0f; icon.scaleY = 0f; icon.rotation = -15f; icon.alpha = 0f
        icon.animate()
            .scaleX(1f).scaleY(1f).rotation(0f).alpha(1f)
            .setDuration(520).setStartDelay(200)
            .setInterpolator(OvershootInterpolator(1.6f)).start()

        // Name
        name.alpha = 0f; name.translationY = 28f
        name.animate().alpha(1f).translationY(0f)
            .setDuration(420).setStartDelay(760)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()

        // Tagline
        tagline.alpha = 0f; tagline.translationY = 18f
        tagline.animate().alpha(1f).translationY(0f)
            .setDuration(360).setStartDelay(980)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()

        // Dots
        listOf(dot1, dot2, dot3).forEachIndexed { i, dot ->
            dot.alpha = 0f
            dot.animate().alpha(1f).setDuration(220).setStartDelay((1420 + i * 160).toLong()).start()
        }

        // Navigate to home
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3000)
    }
}
