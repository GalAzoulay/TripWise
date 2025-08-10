package com.example.tripwise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tripwise.data.TimelineItem
import com.example.tripwise.databinding.ItemTimelineBinding

class TimelineAdapter(
    private val timelineItems: MutableList<TimelineItem>,
    private val onEditClick: (TimelineItem) -> Unit,
    private val onDeleteClick: (TimelineItem) -> Unit
) : RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder>() {

    inner class TimelineViewHolder(private val binding: ItemTimelineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TimelineItem) {
            binding.tvTimelineTime.text = item.time
            binding.tvTimelineDescription.text = item.description
            binding.tvTimelineNotes.text = item.notes
            binding.btnEditTimelineItem.setOnClickListener { onEditClick(item) }
            binding.btnDeleteTimelineItem.setOnClickListener { onDeleteClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val binding = ItemTimelineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimelineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        holder.bind(timelineItems[position])
    }

    override fun getItemCount(): Int = timelineItems.size

    fun updateData(newItems: List<TimelineItem>) {
        timelineItems.clear()
        timelineItems.addAll(newItems)
        notifyDataSetChanged()
    }
}