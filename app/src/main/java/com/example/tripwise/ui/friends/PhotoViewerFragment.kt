package com.example.tripwise.ui.friends

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tripwise.R
import com.example.tripwise.databinding.FragmentPhotoViewerBinding
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2

class PhotoViewerFragment : Fragment() {

    private var _binding: FragmentPhotoViewerBinding? = null
    private val binding get() = _binding!!

    private val args: PhotoViewerFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the arguments from the navigation component
        val photoUrls = args.photoUrls.toList()
        val startPosition = args.startPosition

        // Set up the ViewPager with an adapter
        binding.viewPager.adapter = PhotoViewerAdapter(photoUrls)

        // Set the ViewPager to the starting position
        binding.viewPager.setCurrentItem(startPosition, false)

        binding.btnClose.setOnClickListener {
            // Navigate back to the previous fragment
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class PhotoViewerAdapter(private val photoUrls: List<String>) :
        RecyclerView.Adapter<PhotoViewerAdapter.PhotoViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_full_size_photo, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val imageUrl = photoUrls[position]
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .into(holder.imageView)
        }

        override fun getItemCount(): Int = photoUrls.size

        inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.iv_full_size_photo)
        }
    }
}