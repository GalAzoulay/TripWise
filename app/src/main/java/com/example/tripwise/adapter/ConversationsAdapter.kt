package com.example.tripwise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tripwise.R
import com.example.tripwise.data.Conversation

class ConversationsAdapter(
    private val onConversationClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationsAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profilePicture: ImageView = itemView.findViewById(R.id.iv_other_user_profile_picture)
        private val username: TextView = itemView.findViewById(R.id.tv_other_username)
        private val lastMessage: TextView = itemView.findViewById(R.id.tv_last_message)

        fun bind(conversation: Conversation) {
            Glide.with(itemView.context)
                .load(conversation.otherUserProfilePictureUrl)
                .placeholder(R.drawable.ic_user_circle)
                .into(profilePicture)
            username.text = conversation.otherUserUsername
            lastMessage.text = conversation.lastMessage

            itemView.setOnClickListener {
                onConversationClick(conversation)
            }
        }
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem == newItem
        }
    }
}