package com.example.tripwise.ui.profile

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.tripwise.databinding.FragmentCreateUsernameBinding
import com.example.tripwise.ui.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class CreateUsernameDialogFragment : DialogFragment() {

    private var _binding: FragmentCreateUsernameBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCancelable(false) // Prevent user from dismissing the dialog
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateUsernameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            auth = it.auth
            db = it.db
        }

        binding.btnSaveUsername.setOnClickListener {
            saveUsername()
        }
    }

    private fun saveUsername() {
        val username = binding.etUsername.text.toString().trim()
        val currentUserId = auth.currentUser?.uid

        if (username.isEmpty()) {
            binding.tilUsername.error = "Username cannot be empty"
            return
        }

        if (currentUserId == null) {
            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading state
        binding.pbLoading.visibility = View.VISIBLE
        binding.btnSaveUsername.visibility = View.GONE
        binding.tilUsername.error = null

        // Check if username is already taken
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    // Username is available, save it
                    val userDocRef = db.collection("users").document(currentUserId)
                    userDocRef.update("username", username)
                        .addOnSuccessListener {
                            dismiss() // Dismiss the dialog on success
                            Toast.makeText(context, "Username set successfully!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Log.e("CreateUsernameDialog", "Error updating username", e)
                            binding.tilUsername.error = "Error saving username. Please try again."
                            binding.pbLoading.visibility = View.GONE
                            binding.btnSaveUsername.visibility = View.VISIBLE
                        }
                } else {
                    // Username is already taken
                    binding.tilUsername.error = "This username is already taken. Please choose another."
                    binding.pbLoading.visibility = View.GONE
                    binding.btnSaveUsername.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Log.e("CreateUsernameDialog", "Error checking username uniqueness", e)
                binding.tilUsername.error = "Error checking username. Please try again."
                binding.pbLoading.visibility = View.GONE
                binding.btnSaveUsername.visibility = View.VISIBLE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}