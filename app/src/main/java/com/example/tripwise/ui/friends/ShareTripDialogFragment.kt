package com.example.tripwise.ui.friends

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tripwise.adapter.TripPhotoSelectionAdapter
import com.example.tripwise.adapter.TripSelectionAdapter
import com.example.tripwise.databinding.DialogShareTripBinding
import com.example.tripwise.data.SharedTrip
import com.example.tripwise.data.Trip
import com.example.tripwise.ui.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import androidx.navigation.fragment.findNavController
import com.example.tripwise.R

class ShareTripDialogFragment : DialogFragment() {

    private var _binding: DialogShareTripBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var appId: String
    private var userId: String? = null

    private var tripsListener: ListenerRegistration? = null
    private var selectedTrip: Trip? = null
    private val selectedPhotos = mutableListOf<String>()

    private var sharedTripToEdit: SharedTrip? = null
    private var isEditMode: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogShareTripBinding.inflate(inflater, container, false)
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            appId = it.appId
            db = it.db
            auth = it.auth
            userId = it.userId

            sharedTripToEdit = arguments?.getParcelable("sharedTrip")
            isEditMode = sharedTripToEdit != null

            setupUI()
            setupListeners()
        }
    }

    private fun setupUI() {
        if (isEditMode) {
            binding.tvTitle.text = getString(R.string.edit_post)
            binding.btnShare.text = getString(R.string.update)
            binding.clTripSelection.visibility = View.GONE
            binding.clPhotoAndTextSelection.visibility = View.VISIBLE
            loadEditData()
        } else {
            binding.tvTitle.text = getString(R.string.share_a_trip)
            binding.btnShare.text = getString(R.string.share)
            fetchUserTrips()
        }
    }

    private fun loadEditData() {
        sharedTripToEdit?.let { sharedTrip ->
            binding.etSharedText.setText(sharedTrip.sharedText)
            binding.clPhotoAndTextSelection.visibility = View.GONE

            // Fetch the original trip to get ALL its photos
            sharedTrip.originalTripId.let { originalTripId ->
                db.collection("artifacts").document(appId)
                    .collection("users").document(sharedTrip.userId)
                    .collection("trips").document(originalTripId)
                    .get()
                    .addOnSuccessListener { doc ->
                        val originalTrip = doc.toObject(Trip::class.java)
                        originalTrip?.let { trip ->
                            // Use the full list of photos from the original trip
                            if (trip.imageUrls.isNotEmpty()) {
                                binding.rvPhotoSelection.visibility = View.VISIBLE
                                binding.tvNoPhotosAvailable.visibility = View.GONE
                                // Pass the full list of trip photos and the pre-selected ones
                                setupPhotoSelection(trip.imageUrls, sharedTrip.imageUrls)
                            } else {
                                binding.rvPhotoSelection.visibility = View.GONE
                                binding.tvNoPhotosAvailable.visibility = View.VISIBLE
                            }
                        }
                        binding.clPhotoAndTextSelection.visibility = View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Log.e("ShareTripDialog", "Failed to fetch original trip for editing", e)
                        Toast.makeText(context, "Failed to load photos for editing.", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
            }
        }
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { findNavController().popBackStack() }
        binding.btnShare.setOnClickListener {
            if (isEditMode) {
                updateSharedTrip()
            } else {
                shareTrip()
            }
        }
    }

    private fun fetchUserTrips() {
        userId?.let { uid ->
            val tripsCollectionRef = db.collection("artifacts").document(appId)
                .collection("users").document(uid)
                .collection("trips")

            tripsListener = tripsCollectionRef.addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ShareTripDialog", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    Log.d("ShareTripDialog", "Received ${snapshots.documents.size} documents from Firestore.")
                    val trips = snapshots.toObjects(Trip::class.java)
                    if (trips.isEmpty()) {
                        binding.clTripSelection.visibility = View.GONE
                        binding.clPhotoAndTextSelection.visibility = View.GONE
                        binding.tvNoTripsToShare.visibility = View.VISIBLE
                        binding.btnShare.isEnabled = false
                    } else {
                        binding.tvNoTripsToShare.visibility = View.GONE
                        binding.clTripSelection.visibility = View.VISIBLE
                        binding.btnShare.isEnabled = true
                        setupTripSelection(trips)
                    }
                }
            }
        }
    }

    private fun setupTripSelection(trips: List<Trip>) {
        val adapter = TripSelectionAdapter(trips) { trip ->
            selectedTrip = trip
            binding.tvSelectedTripTitle.text = trip.destination
            binding.clTripSelection.visibility = View.GONE
            binding.clPhotoAndTextSelection.visibility = View.VISIBLE
            selectedPhotos.clear()
            if (trip.imageUrls.isEmpty()) {
                binding.rvPhotoSelection.visibility = View.GONE
                binding.tvNoPhotosAvailable.visibility = View.VISIBLE
            } else {
                binding.rvPhotoSelection.visibility = View.VISIBLE
                binding.tvNoPhotosAvailable.visibility = View.GONE
                setupPhotoSelection(trip.imageUrls, emptyList())
            }
        }
        binding.rvTripSelection.layoutManager = LinearLayoutManager(context)
        binding.rvTripSelection.adapter = adapter
    }

    private fun setupPhotoSelection(photos: List<String>, preSelectedPhotos: List<String>) {
        val adapter = TripPhotoSelectionAdapter(photos, preSelectedPhotos) { photoUrl, isSelected ->
            if (isSelected) {
                selectedPhotos.add(photoUrl)
            } else {
                selectedPhotos.remove(photoUrl)
            }
        }
        binding.rvPhotoSelection.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvPhotoSelection.adapter = adapter
    }

    private fun updateSharedTrip() {
        val tripId = sharedTripToEdit?.id ?: return
        val newCaption = binding.etSharedText.text.toString().trim()
        val photosToShare = selectedPhotos.toList()

        if (photosToShare.isEmpty() && newCaption.isEmpty()) {
            Toast.makeText(context, "Please select at least one photo or add a caption.", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedData = hashMapOf(
            "sharedText" to newCaption,
            "imageUrls" to photosToShare,
            "timestamp" to FieldValue.serverTimestamp() // Update the timestamp
        )

        val sharedTripDocRef = db.collection("artifacts").document(appId)
            .collection("public").document("data")
            .collection("sharedTrips").document(tripId)

        sharedTripDocRef.update(updatedData as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(context, "Post updated successfully!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .addOnFailureListener { e ->
                Log.e("ShareTripDialog", "Error updating trip: ${e.message}", e)
                Toast.makeText(context, "Failed to update post: ${e.message}", Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
    }

    private fun shareTrip() {
        val trip = selectedTrip
        val caption = binding.etSharedText.text.toString()
        val photosToShare = if (selectedPhotos.isEmpty()) trip?.imageUrls ?: emptyList() else selectedPhotos

        if (trip != null && photosToShare.isNotEmpty() || caption.isNotEmpty()) {
            val user = auth.currentUser
            if (user != null) {
                // Get the user's current profile data to include in the shared post
                db.collection("users").document(user.uid).get()
                    .addOnSuccessListener { doc ->
                        val username = doc.getString("username") ?: user.displayName ?: "Anonymous"
                        val profilePictureUrl = doc.getString("profilePictureUrl")

                        val sharedTrip = trip?.let {
                            SharedTrip(
                                userId = user.uid,
                                username = username,
                                profilePictureUrl = profilePictureUrl,
                                sharedText = caption,
                                imageUrls = photosToShare,
                                originalTripId = it.id,
                            )
                        }

                        // Add the new document to the public sharedTrips collection
                        if (sharedTrip != null) {
                            db.collection("artifacts").document(appId)
                                .collection("public").document("data")
                                .collection("sharedTrips")
                                .add(sharedTrip)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Trip shared successfully!", Toast.LENGTH_SHORT).show()
                                    findNavController().popBackStack()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ShareTripDialog", "Error sharing trip", e)
                                    Toast.makeText(context, "Failed to share trip: ${e.message}", Toast.LENGTH_LONG).show()
                                    findNavController().popBackStack()
                                }
                        }
                    }
            }
        } else {
            Toast.makeText(context, "Please select at least one photo or add a caption.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tripsListener?.remove()
        _binding = null
    }
}