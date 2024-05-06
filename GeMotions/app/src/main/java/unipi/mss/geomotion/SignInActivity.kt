package unipi.mss.geomotion



import android.app.UiModeManager
import android.content.ContentValues.TAG
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import unipi.mss.geomotion.MainActivity

class SignInActivity : AppCompatActivity(){
    // get FirebaseAuth object to handle authentication
    private val auth = FirebaseAuth.getInstance()

    // object representing elements on screen
    private lateinit var editTextEmail : TextInputEditText
    private lateinit var editTextPassword : TextInputEditText
    private lateinit var buttonReg : Button
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
        setContentView(R.layout.activity_sign_in)

        Log.d(TAG,"Setting up manual registration")
        // --------- Find objects and add listener for password login ---------
        editTextEmail = findViewById(R.id.email)
        editTextPassword = findViewById(R.id.password)
        buttonReg = findViewById(R.id.btn_register)
        progressBar = findViewById(R.id.progressBar)

        buttonReg.setOnClickListener { signCredential() }

        textView = findViewById(R.id.loginNow)
        textView.setOnClickListener{goToLogin()}
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun signCredential() {
        progressBar.visibility = View.VISIBLE
        val email = editTextEmail.text
        val password = editTextPassword.text

        if (email.isNullOrBlank()){
            Toast.makeText(this,"Enter email",Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isNullOrBlank()){
            Toast.makeText(this,"Enter password",Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email.toString(), password.toString())
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    progressBar.visibility = View.GONE
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "createUserWithEmail:success")
                    Toast.makeText(baseContext, "Account successfully created.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()

                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()

                }
            }
    }
}

