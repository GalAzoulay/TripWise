package com.example.tripwise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tripwise.R
import com.example.tripwise.data.Trip
import com.google.android.material.card.MaterialCardView

class TripSelectionAdapter(
    private val trips: List<Trip>,
    private val onTripSelected: (Trip) -> Unit
) : RecyclerView.Adapter<TripSelectionAdapter.TripViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trip_card_small, parent, false)
        return TripViewHolder(view)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

    override fun getItemCount(): Int = trips.size

    inner class TripViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tv_trip_title)
        private val card: MaterialCardView = itemView.findViewById(R.id.card_trip_small)

        fun bind(trip: Trip) {
            title.text = trip.destination
            card.setOnClickListener {
                onTripSelected(trip)
            }
        }
    }
}