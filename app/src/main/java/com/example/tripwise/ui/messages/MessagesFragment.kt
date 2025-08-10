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
import com.example.tripwise.R
import com.example.tripwise.adapter.ConversationsAdapter
import com.example.tripwise.data.Conversation
import com.example.tripwise.databinding.FragmentMessagesBinding
import com.example.tripwise.ui.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var appId: String
    private var userId: String? = null

    private lateinit var conversationsAdapter: ConversationsAdapter
    private var conversationsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
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
            setupConversationsRecyclerView()
            fetchConversations()
        } ?: run {
            Log.e("MessagesFragment", "MainActivity is not ready.")
            Toast.makeText(context, "App initialization error.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        binding.btnStartNewConversation.setOnClickListener {
            // Navigate to the fragment that shows the list of friends
            val action = MessagesFragmentDirections.actionMessagesFragmentToFriendsListFragment()
            findNavController().navigate(action)
        }
    }

    private fun setupConversationsRecyclerView() {
        conversationsAdapter = ConversationsAdapter { conversation ->
            // Navigate to the chat screen with the selected conversation
            val otherUserId = conversation.participants.first { it != userId }
            val action = MessagesFragmentDirections.actionMessagesFragmentToChatFragment(
                conversationId = conversation.id,
                otherUserId = otherUserId,
                otherUsername = conversation.otherUserUsername
            )
            findNavController().navigate(action)
        }
        binding.rvConversations.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = conversationsAdapter
        }
    }

    private fun fetchConversations() {
        userId?.let { uid ->
            conversationsListener?.remove()

            // Query for conversations where the current user is a participant
            val conversationsCollectionRef = db.collection("artifacts").document(appId)
                .collection("users").document(uid)
                .collection("conversations")
                .orderBy("lastUpdated", Query.Direction.DESCENDING)

            conversationsListener = conversationsCollectionRef.addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("MessagesFragment", "Conversations listener failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val conversations = snapshots.toObjects(Conversation::class.java)
                    conversationsAdapter.submitList(conversations)
                    binding.tvNoConversations.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        conversationsListener?.remove()
        _binding = null
    }
}
