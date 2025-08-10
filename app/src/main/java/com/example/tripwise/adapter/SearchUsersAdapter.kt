package com.example.tripwise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tripwise.R
import com.example.tripwise.data.User
import de.hdodenhof.circleimageview.CircleImageView

class SearchUsersAdapter(
    private val onUserClick: (String) -> Unit
) : ListAdapter<User, SearchUsersAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_search, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profilePicture: CircleImageView = itemView.findViewById(R.id.iv_user_profile_picture)
        private val username: TextView = itemView.findViewById(R.id.tv_user_username)

        fun bind(user: User) {
            username.text = user.username
            if (!user.profilePictureUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(user.profilePictureUrl)
                    .placeholder(R.drawable.ic_user_circle)
                    .into(profilePicture)
            } else {
                profilePicture.setImageResource(R.drawable.ic_user_circle)
            }

            itemView.setOnClickListener {
                onUserClick(user.uid)
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}
