package com.example.tripwise.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tripwise.R
import com.example.tripwise.data.SharedTrip
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SharedTripAdapter(
    private val onUsernameClick: (String) -> Unit,
    private val onProfilePhotoClick: (String) -> Unit,
    private val onTripPhotosClick: (List<String>, Int) -> Unit,
    private val onEditTrip: (SharedTrip) -> Unit,
    private val onDeleteTrip: (String) -> Unit,
    private val currentUserId: String
) : ListAdapter<SharedTrip, SharedTripAdapter.SharedTripViewHolder>(SharedTripDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharedTripViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shared_trip, parent, false)
        return SharedTripViewHolder(view)
    }

    override fun onBindViewHolder(holder: SharedTripViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SharedTripViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profilePicture: ImageView = itemView.findViewById(R.id.iv_shared_profile_picture)
        private val username: TextView = itemView.findViewById(R.id.tv_shared_username)
        private val timestamp: TextView = itemView.findViewById(R.id.tv_shared_timestamp)
        private val sharedText: TextView = itemView.findViewById(R.id.tv_shared_text)
        private val photoContainer: ConstraintLayout = itemView.findViewById(R.id.cl_shared_photos)
        private val firstPhoto: ImageView = itemView.findViewById(R.id.iv_shared_photo_1)
        private val secondPhoto: ImageView = itemView.findViewById(R.id.iv_shared_photo_2)
        private val morePhotosOverlay: View = itemView.findViewById(R.id.view_more_photos_overlay)
        private val morePhotosCount: TextView = itemView.findViewById(R.id.tv_more_photos_count)
        private val optionsButton: ImageView = itemView.findViewById(R.id.btn_shared_options)

        fun bind(sharedTrip: SharedTrip) {
            // Load user data
            Glide.with(itemView.context)
                .load(sharedTrip.profilePictureUrl)
                .placeholder(R.drawable.ic_user_circle)
                .into(profilePicture)
            username.text = sharedTrip.username
            sharedText.text = sharedTrip.sharedText

            sharedTrip.timestamp?.let { date ->
                timestamp.text = formatDate(date)
                timestamp.visibility = View.VISIBLE
            } ?: run {
                timestamp.visibility = View.GONE
            }

            profilePicture.setOnClickListener { sharedTrip.profilePictureUrl?.let { it1 ->
                onProfilePhotoClick(it1) } }
            username.setOnClickListener { onUsernameClick(sharedTrip.userId) }

            // Handle photos album
            val photos = sharedTrip.imageUrls
            if (photos.isNotEmpty()) {
                photoContainer.visibility = View.VISIBLE

                // Always loads the first photo
                Glide.with(itemView.context).load(photos[0]).into(firstPhoto)
                firstPhoto.setOnClickListener { onTripPhotosClick(photos, 0) }

                if (photos.size > 1) {
                    secondPhoto.visibility = View.VISIBLE
                    Glide.with(itemView.context).load(photos[1]).into(secondPhoto)
                    secondPhoto.setOnClickListener { onTripPhotosClick(photos, 1) }

                    // Show overlay if there are more than 2 photos
                    if (photos.size > 2) {
                        morePhotosOverlay.visibility = View.VISIBLE
                        morePhotosCount.text = "+${photos.size - 1}"
                        morePhotosCount.visibility = View.VISIBLE
                        val onOverlayClickListener = View.OnClickListener { onTripPhotosClick(photos, 1) }
                        morePhotosOverlay.setOnClickListener(onOverlayClickListener)
                        secondPhoto.setOnClickListener(onOverlayClickListener)
                    } else {
                        morePhotosOverlay.visibility = View.GONE
                        morePhotosCount.visibility = View.GONE
                    }
                } else {
                    secondPhoto.visibility = View.GONE
                    morePhotosOverlay.visibility = View.GONE
                    morePhotosCount.visibility = View.GONE
                }
            } else {
                photoContainer.visibility = View.GONE
            }

            // Show options button only for the current user's own shared trips
            optionsButton.visibility =
                if (sharedTrip.userId == currentUserId) View.VISIBLE else View.GONE
            optionsButton.setOnClickListener {
                showPopupMenu(optionsButton, sharedTrip)
            }
        }

        private fun formatDate(date: Date): String {
            val now = Date()
            val diff = now.time - date.time
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            return when {
                days > 0 -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
                hours > 0 -> "$hours h ago"
                minutes > 0 -> "$minutes m ago"
                else -> "Just now"
            }
        }

        private fun showPopupMenu(view: View, sharedTrip: SharedTrip) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.post_options_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit_post -> {
                        onEditTrip(sharedTrip)
                        true
                    }
                    R.id.action_delete_post -> {
                        onDeleteTrip(sharedTrip.id)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    class SharedTripDiffCallback : DiffUtil.ItemCallback<SharedTrip>() {
        override fun areItemsTheSame(oldItem: SharedTrip, newItem: SharedTrip): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SharedTrip, newItem: SharedTrip): Boolean {
            return oldItem == newItem
        }
    }
}
