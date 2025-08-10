package com.example.tripwise.ui.timeline

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tripwise.R
import com.example.tripwise.adapter.TimelineAdapter
import com.example.tripwise.data.TimelineItem
import com.example.tripwise.data.Trip
import com.example.tripwise.databinding.FragmentTripTimelineBinding
import com.example.tripwise.ui.MainActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TripTimelineFragment : Fragment() {

    private var _binding: FragmentTripTimelineBinding? = null
    private val binding get() = _binding!!

    private val args: TripTimelineFragmentArgs by navArgs()
    private var currentTrip: Trip? = null

    private lateinit var db: FirebaseFirestore
    private var userId: String? = null
    private lateinit var appId: String

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormatter = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.US)

    private var currentDisplayCalendar: Calendar = Calendar.getInstance()
    private var tripStartCalendar: Calendar = Calendar.getInstance()
    private var tripEndCalendar: Calendar = Calendar.getInstance()

    // Timeline RecyclerView and Adapter
    private lateinit var timelineAdapter: TimelineAdapter
    private var timelineListener: ListenerRegistration? = null

    private var authStateListener: FirebaseAuth.AuthStateListener? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        timelineAdapter = TimelineAdapter(
            mutableListOf(),
            onEditClick = { item -> editTimelineItem(item) },
            onDeleteClick = { item -> deleteTimelineItem(item) }
        )
        binding.rvTimelineItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = timelineAdapter
            isNestedScrollingEnabled = false
        }
        Log.d("TripTimelineFragment", "timelineAdapter initialized in onViewCreated.")

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            // Setup the AuthStateListener for the fragment
            authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    db = mainActivity.db
                    userId = mainActivity.userId
                    appId = mainActivity.appId

                    if (mainActivity.isAuthReady && userId != null) {
                        loadTripDetails()
                        setupTimelineNavigation()
                        setupListeners()
                    } else {
                        Log.e("TripTimelineFragment", "Auth state changed, but MainActivity's Firebase/User ID not fully ready yet. Waiting...")
                    }
                } else {
                    Log.d("TripTimelineFragment", "User signed out or not authenticated.")
                    Snackbar.make(binding.root, "Please sign in to view timelines.", Snackbar.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
            }
            mainActivity.auth.addAuthStateListener(authStateListener!!)
        } else {
            Log.e("TripTimelineFragment", "MainActivity is null, cannot access Firebase services.")
            Snackbar.make(binding.root, "App initialization error. Please restart.", Snackbar.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }


    private fun loadTripDetails() {
        val tripId = args.trip?.id // Use the Parcelable Trip from Safe Args
        if (tripId == null) {
            Snackbar.make(binding.root, "No trip selected.", Snackbar.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        userId?.let { uid ->
            db.collection("artifacts").document(appId)
                .collection("users").document(uid)
                .collection("trips").document(tripId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        currentTrip = documentSnapshot.toObject(Trip::class.java)?.copy(id = documentSnapshot.id, userId = uid)
                        currentTrip?.let { trip ->
                            binding.tvTripTitle.text = trip.destination
                            try {
                                tripStartCalendar.time = dateFormatter.parse(trip.startDate)!!
                                tripEndCalendar.time = dateFormatter.parse(trip.endDate)!!
                                currentDisplayCalendar.time = tripStartCalendar.time // Start displaying from trip's start date
                                updateDisplayedDate()
                                updateNavigationButtonStates()
                                listenForTimelineItems(trip.id) // Start listening for timeline items for this trip
                            } catch (e: Exception) {
                                Log.e("TripTimelineFragment", "Error parsing trip dates for timeline: ${e.message}")
                                Snackbar.make(binding.root, "Error loading trip dates.", Snackbar.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Snackbar.make(binding.root, "Trip not found.", Snackbar.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("TripTimelineFragment", "Error loading trip details: ${e.message}")
                    Snackbar.make(binding.root, "Error loading trip details: ${e.message}", Snackbar.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
        } ?: run {
            Log.e("TripTimelineFragment", "User ID is null, cannot load trip details.")
            findNavController().popBackStack()
        }
    }

    private fun setupTimelineNavigation() {
        binding.btnPreviousDay.setOnClickListener {
            currentDisplayCalendar.add(Calendar.DAY_OF_YEAR, -1)
            updateDisplayedDate()
            updateNavigationButtonStates()
            listenForTimelineItems(currentTrip?.id) // Refresh timeline items for the new date
        }

        binding.btnNextDay.setOnClickListener {
            currentDisplayCalendar.add(Calendar.DAY_OF_YEAR, 1)
            updateDisplayedDate()
            updateNavigationButtonStates()
            listenForTimelineItems(currentTrip?.id) // Refresh timeline items for the new date
        }
    }

    private fun updateDisplayedDate() {
        binding.tvCurrentDay.text = displayDateFormatter.format(currentDisplayCalendar.time)
    }

    private fun updateNavigationButtonStates() {
        // Enable Previous Day button if current day is after the trip start day
        binding.btnPreviousDay.visibility = if (currentDisplayCalendar.after(tripStartCalendar)) View.VISIBLE else View.INVISIBLE
        // Enable Next Day button if current day is before the trip end day
        binding.btnNextDay.visibility = if (currentDisplayCalendar.before(tripEndCalendar)) View.VISIBLE else View.INVISIBLE
    }

    // Sets up a real-time listener for timeline items for the current trip and selected date.
    private fun listenForTimelineItems(tripId: String?) {
        timelineListener?.remove() // Remove previous listener

        if (tripId == null || userId == null) {
            Log.e("TripTimelineFragment", "Cannot listen for timeline items: tripId or userId is null.")
            timelineAdapter.updateData(emptyList()) // Clear adapter if no valid IDs
            return
        }

        val selectedDateFormatted = dateFormatter.format(currentDisplayCalendar.time)

        val timelineCollectionRef = db.collection("artifacts").document(appId)
            .collection("users").document(userId!!)
            .collection("trips").document(tripId)
            .collection("timeline")

        timelineListener = timelineCollectionRef
            .whereEqualTo("date", selectedDateFormatted) // Filter by selected date
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("TripTimelineFragment", "Listen for timeline items failed.", e)
                    Snackbar.make(binding.root, "Failed to load timeline items: ${e.message}", Snackbar.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val items = mutableListOf<TimelineItem>()
                    for (doc in snapshots.documents) {
                        try {
                            val item = doc.toObject(TimelineItem::class.java)
                            if (item != null) {
                                items.add(item.copy(id = doc.id, tripId = tripId)) // Ensure ID and tripId are set
                            }
                        } catch (ex: Exception) {
                            Log.e("TripTimelineFragment", "Error parsing timeline item document: ${doc.id}", ex)
                        }
                    }
                    // Sort items by time before updating the adapter
                    val sortedItems = items.sortedBy { it.time }
                    timelineAdapter.updateData(sortedItems)

                    // Show empty state message if no timeline items for the day
                    binding.tvNoTimelineItems.visibility = if (sortedItems.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvTimelineItems.visibility = if (sortedItems.isEmpty()) View.GONE else View.VISIBLE

                }
            }
    }

    // Handles navigation to edit an existing timeline item
    private fun editTimelineItem(item: TimelineItem) {
        currentTrip?.let { trip ->
            val bundle = Bundle().apply {
                putParcelable("trip", trip)
                putParcelable("timelineItem", item) // Pass the item to edit
            }
            findNavController().navigate(R.id.action_tripTimelineFragment_to_timelineEditorFragment, bundle)
        }
    }

    private fun deleteTimelineItem(item: TimelineItem) {
        userId?.let { uid ->
            currentTrip?.id?.let { tripId ->
                db.collection("artifacts").document(appId)
                    .collection("users").document(uid)
                    .collection("trips").document(tripId)
                    .collection("timeline").document(item.id)
                    .delete()
                    .addOnSuccessListener {
                        Snackbar.make(binding.root, "Timeline item deleted.", Snackbar.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.w("TripTimelineFragment", "Error deleting timeline item", e)
                        Snackbar.make(binding.root, "Error deleting timeline item: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnAddTimelineItem.setOnClickListener {
            currentTrip?.let { trip ->
                val bundle = Bundle().apply {
                    putParcelable("trip", trip)
                    putString("selectedDate", dateFormatter.format(currentDisplayCalendar.time)) // Pass current date
                }
                findNavController().navigate(R.id.action_tripTimelineFragment_to_timelineEditorFragment, bundle)
            } ?: Snackbar.make(binding.root, "Trip data not loaded yet.", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        timelineListener?.remove()
        authStateListener?.let { (activity as? MainActivity)?.auth?.removeAuthStateListener(it) }
        (activity as? MainActivity)?.setBottomNavigationVisibility(true)
    }
}