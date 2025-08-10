package com.example.tripwise.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.tripwise.R
import com.example.tripwise.databinding.ActivityMainBinding
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.firebase.FirebaseOptions
import com.example.tripwise.data.User
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import java.util.Locale

// Declare global variables for Canvas environment.
@JvmField
var __app_id: String = "default-app-id-if-not-set"

@JvmField
var __firebase_config: String = "{}"

@JvmField
var __initial_auth_token: String = ""


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    lateinit var db: FirebaseFirestore
    lateinit var auth: FirebaseAuth
    lateinit var storage: FirebaseStorage

    var userId: String? = null
    @Volatile var isAuthReady: Boolean = false
    lateinit var appId: String

    var userProfile: User? = null

    private lateinit var authStateListener: FirebaseAuth.AuthStateListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        appId = try {
            if (__app_id.isNotBlank()) __app_id else "default-app-id"
        } catch (e: UninitializedPropertyAccessException) {
            Log.e("MainActivity", "Global variable __app_id not initialized, using default.", e)
            "default-app-id"
        }

        setupFirebase()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.tripEditorFragment, R.id.tripTimelineFragment, R.id.timelineEditorFragment -> {
                    binding.bottomNavigationView.visibility = View.GONE
                }
                else -> {
                    binding.bottomNavigationView.visibility = View.VISIBLE
                }
            }
        }
    }

    fun setBottomNavigationVisibility(isVisible: Boolean) {
        binding.bottomNavigationView.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun setupFirebase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Attempt to parse firebaseConfig from the Canvas-provided global variable
                val firebaseConfigJson = try {
                    if (__firebase_config.isNotBlank()) {
                        JSONObject(__firebase_config)
                    } else {
                        Log.e("MainActivity", "Global variable __firebase_config not initialized or empty. Attempting to load from google-services.json.")
                        // Fallback: If Canvas config is not available, FirebaseApp.initializeApp(this) will try to load from google-services.json
                        JSONObject() // Return empty object if not from Canvas
                    }
                } catch (e: UninitializedPropertyAccessException) {
                    Log.e("MainActivity", "Global variable __firebase_config not initialized, creating empty JSON. Attempting to load from google-services.json.", e)
                    JSONObject()
                }

                // Initialize FirebaseApp (this will try to load from google-services.json if not explicit options are provided)
                // Explicitly define options from the parsed JSON, or fallback to direct from google-services.json
                val optionsBuilder = com.google.firebase.FirebaseOptions.Builder()
                // Use optString with a fallback value to avoid IllegalArgumentException if JSON keys are missing/empty
                val apiKey = firebaseConfigJson.optString("apiKey", "AIzaSyAYVnwklPRynkPFD7N9Ko0rDUsVx055yLY")
                val applicationId = firebaseConfigJson.optString("appId", "1:425483151190:android:a365d470cd5a7e020d18f7")
                val projectId = firebaseConfigJson.optString("projectId", "tripwise-app-2025")

                if (apiKey.isNotBlank() && applicationId.isNotBlank() && projectId.isNotBlank()) {
                    optionsBuilder.setApiKey(apiKey)
                    optionsBuilder.setApplicationId(applicationId)
                    optionsBuilder.setProjectId(projectId)
                    val firebaseOptions = optionsBuilder.build()
                    if (FirebaseApp.getApps(this@MainActivity).isEmpty()) {
                        Firebase.initialize(this@MainActivity, firebaseOptions)
                    }
                } else {
                    Log.d("MainActivity", "Firebase config not found. Assuming local google-services.json.")
                    if (FirebaseApp.getApps(this@MainActivity).isEmpty()) {
                        FirebaseApp.initializeApp(this@MainActivity)
                    }
                }

                db = Firebase.firestore
                auth = Firebase.auth
                storage = FirebaseStorage.getInstance()

                authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        userId = user.uid
                        isAuthReady = true
                        Log.d("MainActivity", "User signed in. UID: $userId")
                        fetchUserProfile(user.uid)
                    } else {
                        userId = null
                        userProfile = null
                        isAuthReady = false
                        Log.d("MainActivity", "User is signed out or not authenticated.")

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Check if the Canvas-provided token exists and is not blank
                                if (__initial_auth_token.isNotBlank()) {
                                    Log.d("MainActivity", "Attempting signInWithCustomToken from Canvas...")
                                    auth.signInWithCustomToken(__initial_auth_token).await()
                                } else {
                                    Log.d("MainActivity", "No initial auth token, attempting signInAnonymously...")
                                    auth.signInAnonymously().await()
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Initial authentication (custom token/anonymous) failed:", e)
                                withContext(Dispatchers.Main) {
                                    Snackbar.make(binding.root, "Authentication failed. Please sign in.", Snackbar.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                auth.addAuthStateListener(authStateListener)

            } catch (e: Exception) {
                Log.e("MainActivity", "Error setting up Firebase: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.root.post {
                        Snackbar.make(binding.root, "Failed to initialize app: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun fetchUserProfile(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userProfile = document.toObject(User::class.java)
                } else {
                    // Create a user document if it doesn't exist
                    val newUsername = auth.currentUser?.displayName ?: "User-${uid.substring(0, 5)}"
                    val newLowercaseUsername = newUsername.lowercase(Locale.getDefault())

                    val newUser = User(
                        uid = uid,
                        username = auth.currentUser?.displayName ?: "User-${uid.substring(0, 5)}",
                        email = auth.currentUser?.email ?: "",
                        lowercaseUsername = newLowercaseUsername
                    )
                    db.collection("users").document(uid).set(newUser)
                        .addOnSuccessListener {
                            userProfile = newUser
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error fetching user profile", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::auth.isInitialized) {
            auth.removeAuthStateListener(authStateListener)
        }
    }
}