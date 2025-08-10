package com.example.tripwise.ui.friends

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tripwise.R
import com.example.tripwise.adapter.SharedTripAdapter
import com.example.tripwise.data.SharedTrip
import com.example.tripwise.databinding.FragmentFriendsBinding
import com.example.tripwise.ui.MainActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class FriendsFragment : Fragment() {

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var appId: String
    private var userId: String? = null

    private lateinit var sharedTripAdapter: SharedTripAdapter
    private var sharedTripsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            appId = it.appId
            auth = it.auth
            db = it.db
            userId = it.userId
            setupListeners()
            setupSharedTripsRecyclerView()
            setupSharedTripsListener()
        } ?: run {
            Log.e("FriendsFragment", "MainActivity is not ready.")
            Toast.makeText(context, "App initialization error.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        binding.btnAddFriend.setOnClickListener {
            val action = FriendsFragmentDirections.actionFriendsFragmentToSearchUsersFragment()
            findNavController().navigate(action)
        }

        binding.btnShareTrip.setOnClickListener {
            findNavController().navigate(R.id.action_friendsFragment_to_shareTripDialogFragment)
        }
    }

    private fun setupSharedTripsRecyclerView() {
        sharedTripAdapter = SharedTripAdapter(
            onUsernameClick = { clickedUserId ->
                // Navigate to the profile of the user who posted the trip
                val action = FriendsFragmentDirections.actionFriendsFragmentToProfileFragment(clickedUserId)
                findNavController().navigate(action)
            },
            onProfilePhotoClick = { photo ->
                showPhotoViewerFragment(listOf(photo), 0)
            },
            onTripPhotosClick = { photos, startPosition ->
                showPhotoViewerFragment(photos, startPosition)
            },
            onEditTrip = { sharedTrip ->
                val action = FriendsFragmentDirections.actionFriendsFragmentToShareTripDialogFragment(sharedTrip)
                findNavController().navigate(action)
            },
            onDeleteTrip = { tripId ->
                // Show the custom confirmation dialog before deleting
                val dialog = DeleteConfirmationDialogFragment {
                    deleteSharedTrip(tripId)
                }
                dialog.show(childFragmentManager, "DeleteConfirmationDialog")
            },
            currentUserId = userId ?: ""
        )
        binding.rvSharedTrips.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sharedTripAdapter
        }
    }


    private fun showPhotoViewerFragment(photos: List<String>, startPosition: Int) {
        val action = FriendsFragmentDirections.actionFriendsFragmentToPhotoViewerFragment(
            photoUrls = photos.toTypedArray(),
            startPosition = startPosition
        )
        findNavController().navigate(action)
    }

    private fun setupSharedTripsListener() {
        // Stop any previous listener to prevent duplicates
        sharedTripsListener?.remove()

        val sharedTripsCollectionRef = db.collection("artifacts").document(appId)
            .collection("public").document("data")
            .collection("sharedTrips")

        // Order by timestamp to show the newest trips first
        sharedTripsListener = sharedTripsCollectionRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("FriendsFragment", "Shared trips listener failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val sharedTrips = snapshots.toObjects(SharedTrip::class.java)
                    sharedTripAdapter.submitList(sharedTrips)
                    // Show a message if there are no shared trips
                    binding.tvNoSharedTrips.visibility = if (sharedTrips.isEmpty()) View.VISIBLE else View.GONE
                }
            }
    }


    private fun deleteSharedTrip(tripId: String) {
        val sharedTripDocRef = db.collection("artifacts").document(appId)
            .collection("public").document("data")
            .collection("sharedTrips")
            .document(tripId)

        sharedTripDocRef.delete()
            .addOnSuccessListener {
                Snackbar.make(binding.root, "Post deleted successfully!", Snackbar.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("FriendsFragment", "Error deleting post: ${e.message}", e)
                Snackbar.make(binding.root, "Failed to delete post.", Snackbar.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sharedTripsListener?.remove()
        _binding = null
    }
}