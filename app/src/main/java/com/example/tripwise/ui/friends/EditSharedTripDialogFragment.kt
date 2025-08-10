package com.example.tripwise.ui.friends

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.tripwise.databinding.DialogEditSharedTripBinding

class EditSharedTripDialogFragment(
    private val sharedTripId: String,
    private val currentText: String,
    private val onConfirmUpdate: (String) -> Unit
) : DialogFragment() {

    private var _binding: DialogEditSharedTripBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditSharedTripBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etEditedText.setText(currentText)

        binding.btnSave.setOnClickListener {
            val newText = binding.etEditedText.text.toString().trim()
            if (newText.isNotEmpty()) {
                onConfirmUpdate(newText)
                dismiss()
            } else {
                Toast.makeText(context, "Text cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}