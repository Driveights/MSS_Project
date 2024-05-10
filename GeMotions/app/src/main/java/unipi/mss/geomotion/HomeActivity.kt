package unipi.mss.geomotion

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    // get FirebaseAuth object to handle authentication
    private val auth = FirebaseAuth.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home)

        val logbtn = findViewById<Button>(R.id.loginButton)
        logbtn.setOnClickListener { goToLogin() }

        val signupbtn = findViewById<Button>(R.id.signupButton)
        signupbtn.setOnClickListener { goToRegistration() }

        videoBackground()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        videoBackground()
    }

    private fun videoBackground(){
        val videoView = findViewById<VideoView>(R.id.videoView)

        // Imposta il percorso del video
        val videoPath = "android.resource://" + packageName + "/" + R.raw.home_video_layout

        // Imposta l'URI del video nel VideoView
        videoView.setVideoURI(Uri.parse(videoPath))

        // Avvia la riproduzione del video in loop
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
        }
        videoView.start()
    }


    private fun goToRegistration() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and start MainActivity.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

}
