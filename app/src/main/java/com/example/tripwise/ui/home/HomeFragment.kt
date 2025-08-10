package com.example.tripwise.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tripwise.R
import com.example.tripwise.adapter.TripAdapter
import com.example.tripwise.data.Trip
import com.example.tripwise.data.TripType
import com.example.tripwise.databinding.FragmentHomeBinding
import com.example.tripwise.ui.MainActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bumptech.glide.Glide
import com.google.firebase.firestore.DocumentReference
import com.example.tripwise.ui.profile.CreateUsernameDialogFragment
import com.example.tripwise.data.User
import com.google.firebase.firestore.toObject

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var upcomingTripsAdapter: TripAdapter
    private lateinit var pastTripsAdapter: TripAdapter

    private lateinit var db: FirebaseFirestore
    private var userId: String? = null
    private var tripsListener: ListenerRegistration? = null
    private lateinit var appId: String

    private var profileListener: ListenerRegistration? = null
    private lateinit var profileDocRef: DocumentReference

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            // Setup the AuthStateListener for the fragment
            authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    // User is signed in, get Firebase instances from MainActivity
                    db = mainActivity.db
                    userId = mainActivity.userId
                    appId = mainActivity.appId

                    if (mainActivity.isAuthReady && userId != null) {
                        checkIfUserHasUsername()
                        setupRecyclerViews()
                        setupListeners()
                        listenForProfileUpdates()
                        listenForTrips()
                    } else {
                        Log.e("HomeFragment", "Auth state changed, but MainActivity's Firebase/User ID not fully ready yet. Waiting...")
                    }
                } else {
                    // User is signed out. Clear data or prompt for sign-in.
                    Log.d("HomeFragment", "User signed out or not authenticated. Clearing data.")
                    upcomingTripsAdapter.updateData(mutableListOf())
                    pastTripsAdapter.updateData(mutableListOf())
                    tripsListener?.remove()
                    profileListener?.remove()
                    Snackbar.make(binding.root, "Please sign in to view trips.", Snackbar.LENGTH_LONG).show()
                }
            }
            mainActivity.auth.addAuthStateListener(authStateListener!!)
        } else {
            Log.e("HomeFragment", "MainActivity is null, cannot access Firebase services.")
            Snackbar.make(binding.root, "App initialization error. Please restart.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun checkIfUserHasUsername() {
        userId?.let { uid ->
            db.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                val user = snapshot.toObject(User::class.java)
                if (user?.username.isNullOrEmpty()) {
                    // Show dialog to create a username
                    if (childFragmentManager.findFragmentByTag("CreateUsernameDialog") == null) {
                        CreateUsernameDialogFragment().show(childFragmentManager, "CreateUsernameDialog")
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("HomeFragment", "Error checking user data: ${e.message}", e)
            }
        }
    }


    private fun listenForProfileUpdates() {
        profileListener?.remove()

        userId?.let { uid ->
            profileDocRef = db.collection("artifacts").document(appId)
                .collection("users").document(uid)
                .collection("profile").document("info")

            profileListener = profileDocRef.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("HomeFragment", "Profile listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val profilePictureUrl = snapshot.getString("profilePictureUrl")
                    updateProfilePicture(profilePictureUrl)
                } else {
                    // Document doesn't exist, set a default image
                    updateProfilePicture(null)
                }
            }
        }
    }

    private fun updateProfilePicture(profilePictureUrl: String?) {
        if (profilePictureUrl.isNullOrEmpty()) {
            binding.ivUserIcon.setImageResource(R.drawable.ic_user_circle)
        } else {
            Glide.with(this)
                .load(profilePictureUrl)
                .placeholder(R.drawable.ic_user_circle)
                .error(R.drawable.ic_user_circle)
                .centerCrop()
                .into(binding.ivUserIcon)
        }
    }

    // Initializes and sets up the RecyclerViews for upcoming and past trips.
    private fun setupRecyclerViews() {
        // Initialize with empty lists, data will be loaded from Firestore
        upcomingTripsAdapter = TripAdapter(
            mutableListOf(),
            onEditClick = { trip -> navigateToTripEditor(trip) },
            onTimelineClick = { trip -> navigateToTripTimeline(trip) },
            onDeleteClick = { trip -> deleteTrip(trip) }
        )
        binding.rvUpcomingTrips.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = upcomingTripsAdapter
            isNestedScrollingEnabled = false
        }

        pastTripsAdapter = TripAdapter(
            mutableListOf(),
            onEditClick = { trip -> navigateToTripEditor(trip) },
            onTimelineClick = { trip -> navigateToTripTimeline(trip) },
            onDeleteClick = { trip -> deleteTrip(trip) }
        )
        binding.rvPastTrips.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pastTripsAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        binding.fabAddTrip.setOnClickListener {
            userId?.let { uid ->
                db.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                    val user = snapshot.toObject(User::class.java)
                    if (user?.username.isNullOrEmpty()) {
                        Snackbar.make(binding.root, "Please set a username first.", Snackbar.LENGTH_LONG).show()
                    } else {
                        findNavController().navigate(R.id.action_homeFragment_to_tripEditorFragment)
                    }
                }
            }
        }
        binding.ivUserIcon.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
        }
    }

    // Sets up a real-time listener for trips from Firestore.
    private fun listenForTrips() {
        // Remove previous listener if it exists to avoid duplicates
        tripsListener?.remove()

        userId?.let { uid ->
            val tripsCollectionRef = db.collection("artifacts").document(appId)
                .collection("users").document(uid)
                .collection("trips")

            tripsListener = tripsCollectionRef.addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("HomeFragment", "Listen failed.", e)
                    Snackbar.make(binding.root, "Failed to load trips: ${e.message}", Snackbar.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val allTrips = mutableListOf<Trip>()
                    for (doc in snapshots.documents) {
                        try {
                            val trip = doc.toObject(Trip::class.java)
                            if (trip != null) {
                                allTrips.add(trip.copy(id = doc.id, userId = uid)) // Ensure ID and userId are set
                            }
                        } catch (ex: Exception) {
                            Log.e("HomeFragment", "Error parsing trip document: ${doc.id}", ex)
                        }
                    }
                    updateTripLists(allTrips)
                }
            }
        } ?: run {
            Log.e("HomeFragment", "User ID is null, cannot listen for trips.")
            Snackbar.make(binding.root, "Authentication error. Cannot load trips.", Snackbar.LENGTH_LONG).show()
        }
    }

    // Filters and updates the RecyclerView adapters based on trip dates.
    private fun updateTripLists(trips: List<Trip>) {
        val upcoming = mutableListOf<Trip>()
        val past = mutableListOf<Trip>()
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        for (trip in trips.sortedBy { it.startDate }) { // Sort by start date
            if (trip.startDate.isNotBlank() && trip.startDate >= currentDate) {
                upcoming.add(trip)
            } else {
                past.add(trip)
            }
        }
        upcomingTripsAdapter.updateData(upcoming)
        pastTripsAdapter.updateData(past)

        binding.tvNoUpcomingTrips.visibility = if (upcoming.isEmpty()) View.VISIBLE else View.GONE
        binding.rvUpcomingTrips.visibility = if (upcoming.isEmpty()) View.GONE else View.VISIBLE
        binding.tvNoPastTrips.visibility = if (past.isEmpty()) View.VISIBLE else View.GONE
        binding.rvPastTrips.visibility = if (past.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun navigateToTripEditor(trip: Trip? = null) {
        val bundle = Bundle().apply {
            trip?.let {
                putParcelable("trip", it) // Pass the entire Parcelable Trip object
            }
        }
        findNavController().navigate(R.id.action_homeFragment_to_tripEditorFragment, bundle)
    }

    private fun navigateToTripTimeline(trip: Trip) {
        val bundle = Bundle().apply {
            putParcelable("trip", trip) // Pass the entire Trip object to Timeline
        }
        findNavController().navigate(R.id.action_homeFragment_to_tripTimelineFragment, bundle)
    }

    // Handles the deletion of a trip from Firestore.
    private fun deleteTrip(trip: Trip) {
        userId?.let { uid ->
            db.collection("artifacts").document(appId)
                .collection("users").document(uid)
                .collection("trips").document(trip.id)
                .delete()
                .addOnSuccessListener {
                    Snackbar.make(binding.root, "${trip.destination} deleted successfully.", Snackbar.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.w("HomeFragment", "Error deleting document", e)
                    Snackbar.make(binding.root, "Error deleting ${trip.destination}: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
        } ?: run {
            Log.e("HomeFragment", "User ID is null, cannot delete trip.")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tripsListener?.remove()
        profileListener?.remove()
        authStateListener?.let { (activity as? MainActivity)?.auth?.removeAuthStateListener(it) }
        _binding = null
    }
}