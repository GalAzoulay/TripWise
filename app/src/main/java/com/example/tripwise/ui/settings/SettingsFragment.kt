package com.example.tripwise.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tripwise.R
import com.example.tripwise.databinding.FragmentSettingsBinding
import com.example.tripwise.ui.LoginActivity
import com.firebase.ui.auth.AuthUI
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnAccount.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_profileFragment)
        }

        binding.btnShareProfile.setOnClickListener {
            shareProfile()
        }

        binding.btnSignOut.setOnClickListener {
            signOut()
        }
    }

    private fun shareProfile() {
        val user = auth.currentUser
        if (user != null) {
            val profileLink = "https://www.tripwise.com/profile/${user.uid}" // Placeholder link
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_SUBJECT, "Check out my TripWise Profile!")
                putExtra(Intent.EXTRA_TEXT, "Connect with me on TripWise: $profileLink")
                type = "text/plain"
            }
            // Create a chooser for the user to select an app
            val shareTitle = "Share my TripWise Profile"
            val chooser = Intent.createChooser(shareIntent, shareTitle)
            if (shareIntent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(chooser)
            } else {
                Snackbar.make(binding.root, "No apps found to share with.", Snackbar.LENGTH_SHORT).show()
            }
        } else {
            Snackbar.make(binding.root, "Please sign in to share your profile.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun signOut() {
        auth.signOut()
        Snackbar.make(binding.root, "You have been signed out.", Snackbar.LENGTH_SHORT).show()
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}