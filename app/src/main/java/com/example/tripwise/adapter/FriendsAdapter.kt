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
import com.example.tripwise.data.User

class FriendsAdapter(
    private val onFriendClick: (User) -> Unit
) : ListAdapter<User, FriendsAdapter.FriendViewHolder>(FriendDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_for_chat, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profilePicture: ImageView = itemView.findViewById(R.id.iv_friend_profile_picture)
        private val username: TextView = itemView.findViewById(R.id.tv_friend_username)

        fun bind(user: User) {
            Glide.with(itemView.context)
                .load(user.profilePictureUrl)
                .placeholder(R.drawable.ic_user_circle)
                .into(profilePicture)
            username.text = user.username

            itemView.setOnClickListener {
                onFriendClick(user)
            }
        }
    }

    class FriendDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}