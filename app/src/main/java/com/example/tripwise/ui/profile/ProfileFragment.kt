package com.example.tripwise.ui.profile

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.tripwise.R
import com.example.tripwise.adapter.SharedTripAdapter
import com.example.tripwise.data.SharedTrip
import com.example.tripwise.data.User
import com.example.tripwise.databinding.FragmentProfileBinding
import com.example.tripwise.ui.MainActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.example.tripwise.ui.friends.DeleteConfirmationDialogFragment
import com.example.tripwise.ui.friends.FriendsFragmentDirections
import com.example.tripwise.ui.friends.ShareTripDialogFragment
import com.google.firebase.firestore.toObject

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var appId: String

    private var currentUserId: String? = null
    private var displayedUserId: String? = null

    private var profileListener: ListenerRegistration? = null
    private var sharedTripsListener: ListenerRegistration? = null

    private val args: ProfileFragmentArgs by navArgs()

    private lateinit var sharedTripAdapter: SharedTripAdapter

    // ActivityResultLauncher for picking a single image (photo picker)
    private val pickImageLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                uploadProfilePicture(uri)
            } else {
                Snackbar.make(binding.root, "No image selected.", Snackbar.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            appId = it.appId
            auth = it.auth
            db = it.db
            storage = it.storage
            currentUserId = it.userId

            // Get the user ID from navigation arguments or use the current user's ID
            displayedUserId = args.userId ?: currentUserId

            setupUI()
            setupListeners()
            setupSharedTripsRecyclerView()
            fetchUserData()
            setupSharedTripsListener()
        } ?: run {
            Log.e("ProfileFragment", "MainActivity is not ready or user is not authenticated.")
            Snackbar.make(binding.root, "App initialization error. Please try again.", Snackbar.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    // Shows/hides UI elements based on whether the current user is viewing their own profile
    private fun setupUI() {
        val isCurrentUserProfile = displayedUserId == currentUserId
        binding.btnEditProfilePicture.visibility = if (isCurrentUserProfile) View.VISIBLE else View.GONE
        binding.btnDeleteProfilePicture.visibility = if (isCurrentUserProfile) View.VISIBLE else View.GONE
        binding.addFriendButton.visibility = if (isCurrentUserProfile) View.GONE else View.VISIBLE
    }

    private fun setupSharedTripsRecyclerView() {
        sharedTripAdapter = SharedTripAdapter(
            onUsernameClick = {
                // Do nothing, we are already on the profile page
            },
            onProfilePhotoClick = { photo ->
                showPhotoViewerFragment(listOf(photo), 0)
            },
            onTripPhotosClick = { photos, startPosition ->
                showPhotoViewerFragment(photos, startPosition)
            },
            onEditTrip = { sharedTrip ->
                // Show the dialog to edit the post, pre-filled with the trip data
                val action = ProfileFragmentDirections.actionProfileFragmentToShareTripDialogFragment(sharedTrip)
                findNavController().navigate(action)
            },
            onDeleteTrip = { tripId ->
                val dialog = DeleteConfirmationDialogFragment {
                    deleteSharedTrip(tripId)
                }
                dialog.show(childFragmentManager, "DeleteConfirmationDialog")
            },
            currentUserId = currentUserId ?: ""
        )
        binding.rvSharedTripsProfile.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sharedTripAdapter
        }
    }

    private fun showPhotoViewerFragment(photos: List<String>, startPosition: Int) {
        val action = ProfileFragmentDirections.actionProfileFragmentToPhotoViewerFragment(
            photoUrls = photos.toTypedArray(),
            startPosition = startPosition
        )
        findNavController().navigate(action)
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
                Log.e("ProfileFragment", "Error deleting post: ${e.message}", e)
                Snackbar.make(binding.root, "Failed to delete post.", Snackbar.LENGTH_SHORT).show()
            }
    }

    // Fetches the user's profile data (username and profile picture)
    private fun fetchUserData() {
        displayedUserId?.let { uid ->
            profileListener?.remove()
            val userDocRef = db.collection("users").document(uid)
            profileListener = userDocRef.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ProfileFragment", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    if (user != null) {
                        binding.tvUsername.text = user.username
                        // Load profile picture with Glide
                        if (!user.profilePictureUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(user.profilePictureUrl)
                                .into(binding.ivProfilePicture)
                        } else {
                            binding.ivProfilePicture.setImageResource(R.drawable.ic_user_circle)
                        }
                        user.profilePictureUrl?.let { url ->
                            binding.ivProfilePicture.setOnClickListener {
                                showPhotoViewerFragment(listOf(url), 0)
                            }
                        }
                    }
                }
            }
        }
    }

    // Listens for shared trips for the currently displayed user
    private fun setupSharedTripsListener() {
        displayedUserId?.let { uid ->
            sharedTripsListener?.remove()

            val sharedTripsCollectionRef = db.collection("artifacts").document(appId)
                .collection("public").document("data")
                .collection("sharedTrips")

            sharedTripsListener = sharedTripsCollectionRef
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w("ProfileFragment", "Shared trips listener failed.", e)
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val sharedTrips = snapshots.toObjects(SharedTrip::class.java)
                        // Manually sort the list by timestamp in descending order
                        val sortedTrips = sharedTrips.sortedByDescending { it.timestamp }
                        sharedTripAdapter.submitList(sortedTrips)
                        binding.tvNoSharedTripsProfile.visibility = if (sortedTrips.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnEditProfilePicture.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnDeleteProfilePicture.setOnClickListener {
            deleteProfilePicture()
        }

        binding.addFriendButton.setOnClickListener {
            addFriend()
        }
    }

    private fun addFriend() {
        val currentUid = currentUserId
        val friendUid = displayedUserId

        if (currentUid != null && friendUid != null && currentUid != friendUid) {
            // Reference to the current user's friends collection
            val currentUserFriendsRef = db.collection("artifacts").document(appId)
                .collection("users").document(currentUid)
                .collection("friends")

            // Check if the user is already a friend
            currentUserFriendsRef.document(friendUid).get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    Snackbar.make(binding.root, "You are already friends with this user.", Snackbar.LENGTH_SHORT).show()
                } else {
                    // Get the friend's user data
                    db.collection("users").document(friendUid).get().addOnSuccessListener { friendDoc ->
                        val friendUser = friendDoc.toObject(User::class.java)
                        if (friendUser != null) {
                            // Add friend to current user's friends collection
                            currentUserFriendsRef.document(friendUid).set(friendUser)

                            // Add current user to friend's friends collection
                            db.collection("users").document(currentUid).get().addOnSuccessListener { currentUserDoc ->
                                val currentUser = currentUserDoc.toObject(User::class.java)
                                if (currentUser != null) {
                                    db.collection("artifacts").document(appId)
                                        .collection("users").document(friendUid)
                                        .collection("friends").document(currentUid)
                                        .set(currentUser)
                                        .addOnSuccessListener {
                                            Snackbar.make(binding.root, "Friend added successfully!", Snackbar.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener { e ->
                                            Snackbar.make(binding.root, "Failed to add friend.", Snackbar.LENGTH_SHORT).show()
                                            Log.e("ProfileFragment", "Error adding friend for displayed user: ${e.message}", e)
                                        }
                                }
                            }
                        }
                    }.addOnFailureListener { e ->
                        Snackbar.make(binding.root, "Failed to get friend's user data.", Snackbar.LENGTH_SHORT).show()
                        Log.e("ProfileFragment", "Error getting friend's user data: ${e.message}", e)
                    }
                }
            }.addOnFailureListener { e ->
                Snackbar.make(binding.root, "Failed to check friendship status.", Snackbar.LENGTH_SHORT).show()
                Log.e("ProfileFragment", "Error checking friendship status: ${e.message}", e)
            }
        }
    }

    private fun uploadProfilePicture(imageUri: Uri) {
        currentUserId?.let { uid ->
            val profilePicRef: StorageReference = storage.reference.child("images/users/$uid/profile_picture.jpg")

            // Show progress indicator
            binding.pbProfileUpload.visibility = View.VISIBLE
            binding.btnEditProfilePicture.isEnabled = false

            profilePicRef.putFile(imageUri)
                .addOnSuccessListener {
                    profilePicRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        // Update the 'users' collection with the new URL
                        val userDocRef = db.collection("users").document(uid)
                        userDocRef.update("profilePictureUrl", downloadUri.toString())
                            .addOnSuccessListener {
                                Snackbar.make(binding.root, "Profile picture updated!", Snackbar.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Snackbar.make(binding.root, "Failed to update profile picture URL.", Snackbar.LENGTH_LONG).show()
                            }
                        binding.pbProfileUpload.visibility = View.GONE
                        binding.btnEditProfilePicture.isEnabled = true
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileFragment", "Image upload failed.", e)
                    Snackbar.make(binding.root, "Image upload failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                    binding.pbProfileUpload.visibility = View.GONE
                    binding.btnEditProfilePicture.isEnabled = true
                }
        }
    }

    private fun deleteProfilePicture() {
        currentUserId?.let { uid ->
            val profilePicRef: StorageReference = storage.reference.child("images/users/$uid/profile_picture.jpg")
            profilePicRef.delete()
                .addOnSuccessListener {
                    val userDocRef = db.collection("users").document(uid)
                    userDocRef.update("profilePictureUrl", null)
                        .addOnSuccessListener {
                            Snackbar.make(binding.root, "Profile picture removed.", Snackbar.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    Log.w("ProfileFragment", "Error deleting profile picture from Storage: ${e.message}", e)
                    if (e.message?.contains("Object does not exist") == true) {
                        val userDocRef = db.collection("users").document(uid)
                        userDocRef.update("profilePictureUrl", null)
                            .addOnSuccessListener {
                                Snackbar.make(binding.root, "Profile picture removed.", Snackbar.LENGTH_SHORT).show()
                            }
                    } else {
                        Snackbar.make(binding.root, "Error deleting profile picture: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        profileListener?.remove()
        sharedTripsListener?.remove()
        _binding = null
    }
}