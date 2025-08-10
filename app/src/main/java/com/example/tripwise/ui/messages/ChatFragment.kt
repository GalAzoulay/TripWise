package com.example.tripwise.ui.messages

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tripwise.R
import com.example.tripwise.adapter.MessagesAdapter
import com.example.tripwise.data.Message
import com.example.tripwise.data.Conversation
import com.example.tripwise.databinding.FragmentChatBinding
import com.example.tripwise.ui.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var appId: String
    private var userId: String? = null

    private lateinit var messagesAdapter: MessagesAdapter
    private var messagesListener: ListenerRegistration? = null

    private var otherUserId: String? = null
    private var otherUsername: String? = null
    private var conversationId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
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

            // Get arguments from navigation
            otherUserId = arguments?.getString("otherUserId")
            otherUsername = arguments?.getString("otherUsername")
            conversationId = arguments?.getString("conversationId")

            setupUI()
            setupListeners()
            setupMessagesRecyclerView()
            fetchMessages()
        } ?: run {
            Log.e("ChatFragment", "MainActivity is not ready.")
            Toast.makeText(context, "App initialization error.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUI() {
        binding.tvChatUsername.text = otherUsername ?: "User"
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            sendMessage()
        }
        binding.etMessageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                sendMessage()
                true
            } else {
                false
            }
        }
        binding.clChatHeader.findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupMessagesRecyclerView() {
        messagesAdapter = MessagesAdapter(userId ?: "")
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = messagesAdapter
        }
    }

    private fun fetchMessages() {
        userId?.let { uid ->
            otherUserId?.let { otherUid ->
                messagesListener?.remove()

                val sortedParticipants = listOf(uid, otherUid).sorted()
                val tempConversationId = "${sortedParticipants[0]}_${sortedParticipants[1]}"

                val finalConversationId = conversationId ?: tempConversationId

                val messagesCollectionRef = db.collection("artifacts").document(appId)
                    .collection("conversations").document(finalConversationId)
                    .collection("messages")
                    .orderBy("timestamp", Query.Direction.ASCENDING)

                messagesListener = messagesCollectionRef.addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w("ChatFragment", "Messages listener failed.", e)
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val messages = snapshots.toObjects(Message::class.java)
                        messagesAdapter.submitList(messages)
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val messageText = binding.etMessageInput.text.toString().trim()
        if (messageText.isEmpty()) {
            return
        }

        userId?.let { uid ->
            otherUserId?.let { otherUid ->
                val sortedParticipants = listOf(uid, otherUid).sorted()
                val finalConversationId = conversationId ?: "${sortedParticipants[0]}_${sortedParticipants[1]}"

                val message = Message(
                    senderId = uid,
                    text = messageText,
                    timestamp = null
                )

                db.collection("artifacts").document(appId)
                    .collection("conversations").document(finalConversationId)
                    .collection("messages")
                    .add(message)
                    .addOnSuccessListener {
                        binding.etMessageInput.text.clear()
                        updateConversation(finalConversationId, messageText)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatFragment", "Error sending message", e)
                        Toast.makeText(context, "Failed to send message.", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun updateConversation(convId: String, lastMessageText: String) {
        userId?.let { uid ->
            otherUserId?.let { otherUid ->
                val conversationRef = db.collection("artifacts").document(appId)
                    .collection("conversations").document(convId)

                db.collection("users").document(otherUid).get()
                    .addOnSuccessListener { otherUserDoc ->
                        val otherUsername = otherUserDoc.getString("username") ?: "Unknown"
                        val otherUserProfilePic = otherUserDoc.getString("profilePictureUrl") ?: ""

                        val conversation = Conversation(
                            id = convId,
                            participants = listOf(uid, otherUid),
                            lastMessage = lastMessageText,
                            lastMessageSenderId = uid,
                            otherUserUsername = otherUsername,
                            otherUserProfilePictureUrl = otherUserProfilePic
                        )

                        conversationRef.set(conversation)
                            .addOnSuccessListener {
                                Log.d("ChatFragment", "Conversation updated successfully.")
                            }
                            .addOnFailureListener { e ->
                                Log.e("ChatFragment", "Error updating conversation", e)
                            }

                        val otherUserConversationRef = db.collection("artifacts").document(appId)
                            .collection("users").document(otherUid)
                            .collection("conversations").document(convId)

                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { currentUserDoc ->
                                val currentUsername = currentUserDoc.getString("username") ?: "You"
                                val currentUserProfilePic = currentUserDoc.getString("profilePictureUrl") ?: ""

                                val otherUserConversation = Conversation(
                                    id = convId,
                                    participants = listOf(uid, otherUid),
                                    lastMessage = lastMessageText,
                                    lastMessageSenderId = uid,
                                    otherUserUsername = currentUsername,
                                    otherUserProfilePictureUrl = currentUserProfilePic
                                )

                                otherUserConversationRef.set(otherUserConversation)
                            }
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        messagesListener?.remove()
        _binding = null
    }
}