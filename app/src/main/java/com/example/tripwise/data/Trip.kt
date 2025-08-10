package com.example.tripwise.data

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class Trip(
    @DocumentId val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val destination: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val type: TripType = TripType.UPCOMING,
    val flights: String = "",
    val accommodation: String = "",
    val notes: String = "",
    val imageUrls: List<String> = listOf(),
    val invitedFriends: List<String> = listOf()
) : Parcelable

enum class TripType {
    UPCOMING,
    PAST
}