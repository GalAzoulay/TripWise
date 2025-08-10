package com.example.tripwise.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.example.tripwise.R

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (FirebaseAuth.getInstance().currentUser == null) {
            signIn()
        } else {
            transactToMainActivity()
        }
    }

    private fun transactToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract(),
    ) { res ->
        this.onSignInResult(res)
    }

    private fun signIn() {
        // Choose authentication providers
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build(),
        )
        // Create and launch sign-in intent
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setTheme(R.style.Theme_TripWise)
            .setLogo(R.drawable.tripwiselogo)
            .build()
        signInLauncher.launch(signInIntent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            val user = FirebaseAuth.getInstance().currentUser
            Log.d("LoginActivity", "Sign-in successful for user: ${user?.displayName}")
            transactToMainActivity()
        } else {
            // Sign in failed. If response is null the user canceled the sign-in flow using the back button.
            // Otherwise check response.getError().getErrorCode() and handle the error.
            if (response == null) {
                // User canceled the sign-in flow
                Log.d("LoginActivity", "Sign-in canceled by user.")
                if (FirebaseAuth.getInstance().currentUser == null) {
                    signIn()
                } else {
                    transactToMainActivity()
                }
            } else {
                Log.e("LoginActivity", "Sign-in failed with error code: ${response.error?.errorCode}")
            }
        }
    }
}