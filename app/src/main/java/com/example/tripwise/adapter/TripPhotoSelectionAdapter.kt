package com.example.tripwise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tripwise.R
import com.google.android.material.card.MaterialCardView

class TripPhotoSelectionAdapter(
    private val photoUrls: List<String>,
    private val preSelectedPhotos: List<String>,
    private val onPhotoSelected: (String, Boolean) -> Unit
) : RecyclerView.Adapter<TripPhotoSelectionAdapter.PhotoViewHolder>() {

    private val selectedPhotos = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_selection, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photoUrls[position])
    }

    override fun getItemCount(): Int = photoUrls.size

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val photoImageView: ImageView = itemView.findViewById(R.id.iv_photo_selection)
        private val card: MaterialCardView = itemView.findViewById(R.id.card_photo_selection)
        private val overlay: View = itemView.findViewById(R.id.view_selected_overlay)

        fun bind(photoUrl: String) {
            Glide.with(itemView.context)
                .load(photoUrl)
                .into(photoImageView)

            updateSelectionState(photoUrl)

            itemView.setOnClickListener {
                if (selectedPhotos.contains(photoUrl)) {
                    selectedPhotos.remove(photoUrl)
                    onPhotoSelected(photoUrl, false)
                } else {
                    selectedPhotos.add(photoUrl)
                    onPhotoSelected(photoUrl, true)
                }
                updateSelectionState(photoUrl)
            }
        }

        private fun updateSelectionState(photoUrl: String) {
            if (selectedPhotos.contains(photoUrl)) {
                overlay.visibility = View.VISIBLE
                card.strokeColor = itemView.context.resources.getColor(R.color.black, null)
                card.strokeWidth = 4
            } else {
                overlay.visibility = View.GONE
                card.strokeWidth = 0
            }
        }
    }
}