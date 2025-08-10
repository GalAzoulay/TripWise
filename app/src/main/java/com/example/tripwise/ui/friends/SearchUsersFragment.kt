package com.example.tripwise.ui.friends

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tripwise.adapter.SearchUsersAdapter
import com.example.tripwise.data.User
import com.example.tripwise.databinding.FragmentSearchUsersBinding
import com.example.tripwise.ui.MainActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import java.util.Locale

class SearchUsersFragment : Fragment() {

    private var _binding: FragmentSearchUsersBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var appId: String
    private var userId: String? = null

    private lateinit var searchAdapter: SearchUsersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            db = mainActivity.db
            appId = mainActivity.appId
            userId = mainActivity.userId
            setupListeners()
            setupRecyclerView()
        } else {
            Snackbar.make(binding.root, "App initialization error.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        binding.btnCloseSearch.setOnClickListener {
            findNavController().popBackStack()
        }

        // Set up the TextWatcher for live search
        binding.etSearchUsers.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    searchUsers(query)
                } else {
                    searchAdapter.submitList(emptyList())
                    binding.tvNoResults.visibility = View.GONE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchUsersAdapter { userId ->
            val action = SearchUsersFragmentDirections.actionSearchUsersFragmentToProfileFragment(userId)
            findNavController().navigate(action)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
        }
    }

    private fun searchUsers(query: String) {
        val lowercaseQuery = query.lowercase(Locale.getDefault())
        val usersCollectionRef = db.collection("users")

        // Perform a "starts with" query
        val endQuery = lowercaseQuery + "\uf8ff" // A special character to create a range query

        usersCollectionRef
            .whereGreaterThanOrEqualTo("lowercaseUsername", lowercaseQuery)
            .whereLessThanOrEqualTo("lowercaseUsername", endQuery)
            .orderBy("lowercaseUsername", Query.Direction.ASCENDING)
            .limit(20) // Limit the number of results to improve performance
            .get()
            .addOnSuccessListener { snapshots ->
                val users = snapshots.toObjects(User::class.java)
                searchAdapter.submitList(users)

                binding.tvNoResults.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Log.e("SearchUsersFragment", "Error searching users: ${e.message}", e)
                Snackbar.make(binding.root, "Failed to search for users.", Snackbar.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
