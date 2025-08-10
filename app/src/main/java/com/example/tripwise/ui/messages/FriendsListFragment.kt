package com.example.tripwise.ui.messages

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tripwise.adapter.FriendsAdapter
import com.example.tripwise.data.User
import com.example.tripwise.databinding.FragmentFriendsListBinding
import com.example.tripwise.ui.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject

class FriendsListFragment : Fragment() {

    private var _binding: FragmentFriendsListBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var appId: String
    private var userId: String? = null

    private lateinit var friendsAdapter: FriendsAdapter
    private var friendsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsListBinding.inflate(inflater, container, false)
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
            setupFriendsRecyclerView()
            fetchFriends()
        } ?: run {
            Log.e("FriendsListFragment", "MainActivity is not ready.")
            Toast.makeText(context, "App initialization error.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupFriendsRecyclerView() {
        friendsAdapter = FriendsAdapter { friend ->
            // When a friend is clicked, navigate to the chat screen
            val action = FriendsListFragmentDirections.actionFriendsListFragmentToChatFragment(
                otherUserId = friend.uid,
                otherUsername = friend.username
            )
            findNavController().navigate(action)
        }
        binding.rvFriends.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = friendsAdapter
        }
    }

    private fun fetchFriends() {
        userId?.let { uid ->
            friendsListener?.remove()

            val friendsCollectionRef = db.collection("artifacts").document(appId)
                .collection("users").document(uid)
                .collection("friends")

            friendsListener = friendsCollectionRef.addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("FriendsListFragment", "Friends listener failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val friends = snapshots.toObjects(User::class.java)
                    friendsAdapter.submitList(friends)
                    binding.tvNoFriends.visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        friendsListener?.remove()
        _binding = null
    }
}