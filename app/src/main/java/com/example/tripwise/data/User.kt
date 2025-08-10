package com.example.tripwise.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    @DocumentId
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val lowercaseUsername: String = "",
    val profilePictureUrl: String? = null,
    val friends: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Date? = null
)