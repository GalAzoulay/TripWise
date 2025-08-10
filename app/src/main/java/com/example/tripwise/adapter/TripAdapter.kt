package com.example.tripwise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.tripwise.R
import com.example.tripwise.data.Trip
import com.example.tripwise.data.TripType
import com.example.tripwise.databinding.ItemTripBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TripAdapter(
    private val trips: MutableList<Trip>,
    private val onEditClick: (Trip) -> Unit,
    private val onTimelineClick: (Trip) -> Unit,
    private val onDeleteClick: (Trip) -> Unit
) : RecyclerView.Adapter<TripAdapter.TripViewHolder>() {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    // ViewHolder class to hold the views for a single trip item.
    inner class TripViewHolder(private val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(trip: Trip) {
            binding.tvDestination.text = trip.destination
            val formattedStartDate = try {
                val date = dateFormatter.parse(trip.startDate)
                displayFormatter.format(date!!)
            } catch (e: Exception) {
                trip.startDate
            }
            val formattedEndDate = try {
                val date = dateFormatter.parse(trip.endDate)
                displayFormatter.format(date!!)
            } catch (e: Exception) {
                trip.endDate
            }
            binding.tvDates.text = "$formattedStartDate - $formattedEndDate"

            binding.btnOptions.setOnClickListener {
                showPopupMenu(it, trip)
            }
        }

        private fun showPopupMenu(view: View, trip: Trip) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.trip_options_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit_trip -> {
                        onEditClick(trip)
                        true
                    }
                    R.id.action_trip_timeline -> {
                        onTimelineClick(trip)
                        true
                    }
                    R.id.action_delete_trip -> {
                        onDeleteClick(trip)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

    override fun getItemCount(): Int = trips.size

    // Helper function to update the entire list (for Firestore updates)
    fun updateData(newTrips: List<Trip>) {
        trips.clear()
        trips.addAll(newTrips)
        notifyDataSetChanged()
    }
}
