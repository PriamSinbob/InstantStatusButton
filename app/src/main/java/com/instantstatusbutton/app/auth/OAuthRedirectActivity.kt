package com.instantstatusbutton.app.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import com.instantstatusbutton.app.ui.SettingsActivity
import kotlinx.coroutines.launch

/** Receives `instantstatusbutton://oauth/...` after the user approves access in the browser. */
class OAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val message = OAuthManager.handleRedirect(applicationContext, uri)
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            // Bring the settings screen back to the front (also closes the Custom Tab).
            startActivity(
                Intent(this@OAuthRedirectActivity, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
    }
}
