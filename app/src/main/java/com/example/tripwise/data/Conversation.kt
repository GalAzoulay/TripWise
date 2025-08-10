package com.example.tripwise.data

import android.os.Parcelable
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Conversation(
    var id: String = "",
    var participants: List<String> = listOf(),
    var lastMessage: String = "",
    var lastMessageSenderId: String = "",
    var otherUserUsername: String = "",
    var otherUserProfilePictureUrl: String = "",
    @ServerTimestamp val lastUpdated: Date? = null
) : Parcelable
