package com.example.tripwise.data

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class TimelineItem(
    @DocumentId val id: String = UUID.randomUUID().toString(),
    val tripId: String = "",
    val date: String = "",
    val time: String = "",
    val description: String = "",
    val notes: String = "",
    val imageUrls: List<String> = listOf()
) : Parcelable
