package com.example.tripwise.data

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class SharedTrip(
    @DocumentId
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null,
    val sharedText: String = "",
    val imageUrls: List<String> = listOf(),
    @ServerTimestamp
    val timestamp: Date? = null,
    val originalTripId: String = ""
) : Parcelable