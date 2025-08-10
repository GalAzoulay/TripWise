package com.example.tripwise.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Message(
    var id: String = "",
    var senderId: String = "",
    var text: String = "",
    @ServerTimestamp val timestamp: Date? = null
)
