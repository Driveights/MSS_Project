package unipi.mss.geomotion

import android.app.UiModeManager
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider


class LoginActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener {

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    // get FirebaseAuth object to handle authentication
    private val auth = FirebaseAuth.getInstance()

    // object representing elements on screen
    private lateinit var editTextEmail: TextInputEditText
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var buttonLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textView: TextView

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

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {

        Log.d(TAG,"Setting theme")
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager != null && uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES) {
            // Il tema di sistema è impostato su tema scuro
            setTheme(R.style.AppTheme_Dark_NoActionBar);
        } else {
            // Il tema di sistema non è impostato su tema scuro
            setTheme(R.style.AppTheme_NoActionBar);
        }

        Log.d(TAG,"Layout creation")
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_login)
        Log.d(TAG,"Searching google button")
        // --------------- GOOGLE LOGIN -------------------
        val signInButton = findViewById<ImageButton>(R.id.signInButtonGoogle)
        signInButton.setOnClickListener {
            signInGoogle()
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        val mGoogleApiClient = GoogleApiClient.Builder(this)
            .enableAutoManage(this, this)
            .addApi(Auth.GOOGLE_SIGN_IN_API, gso).build()

        // --------------- END GOOGLE LOGIN -------------------

        // --------- Find objects and add listener for password login ---------
        Log.d(TAG,"Setting up manual login")

        editTextEmail = findViewById(R.id.email)
        editTextPassword = findViewById(R.id.password)
        buttonLogin = findViewById(R.id.btn_login)
        progressBar = findViewById(R.id.progressBar)

        buttonLogin.setOnClickListener { loginCredential() }

        textView = findViewById(R.id.signupNow)
        textView.setOnClickListener { goToRegistration() }
    }

    private fun goToRegistration() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun loginCredential() {
        progressBar.visibility = View.VISIBLE
        val email = editTextEmail.text
        val password = editTextPassword.text

        // Check if email or password are null
        if (email.isNullOrBlank()) {
            Toast.makeText(this, "Enter email", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isNullOrBlank()) {
            Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email.toString(), password.toString())
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {        // if login completed and successful
                    progressBar.visibility = View.GONE      // remove progress bar and then start main activity
                    Log.d(TAG, "signInWithEmail:success")
                    Toast.makeText(baseContext, "Login successful.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()

                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT)
                        .show()

                }
            }
    }

    // ---------------- USEFUL FUNCTION FOR GOOGLE LOGIN
    private fun signInGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithGoogle:success")
                    val user = auth.currentUser
                    Toast.makeText(this, "Signed in as ${user?.displayName}", Toast.LENGTH_SHORT)
                        .show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.w(TAG, "signInWithGoogle:success", task.exception)
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        Log.w(TAG, "signInWithGoogle:connectionFailed")
    }
}
