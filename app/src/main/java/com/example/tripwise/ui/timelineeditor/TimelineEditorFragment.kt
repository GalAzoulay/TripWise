package com.example.tripwise.ui.timelineeditor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.tripwise.data.TimelineItem
import com.example.tripwise.data.Trip
import com.example.tripwise.databinding.FragmentTimelineEditorBinding
import com.example.tripwise.ui.MainActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import com.bumptech.glide.Glide
import com.example.tripwise.R
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

class TimelineEditorFragment : Fragment() {

    private var _binding: FragmentTimelineEditorBinding? = null
    private val binding get() = _binding!!

    private val args: TimelineEditorFragmentArgs by navArgs()
    private lateinit var parentTrip: Trip // The trip this timeline item belongs to
    private var currentTimelineItem: TimelineItem? = null

    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var userId: String? = null
    private lateinit var appId: String

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private val displayTimeFormatter = SimpleDateFormat("h:mm a", Locale.US)

    // Variable to hold the URLs of all selected/uploaded images
    private val selectedImageUrls: MutableList<String> = mutableListOf()

    // ActivityResultLauncher for picking multiple images using PickMultipleVisualMedia
    private val pickImagesLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(PickMultipleVisualMedia(5)) { uris: List<Uri>? -> // Allow up to 5 images
            uris?.let {
                if (it.isNotEmpty()) {
                    Log.d("PhotoPicker", "Number of items selected: ${it.size}")
                    uploadImagesToFirebaseStorage(it)
                } else {
                    Log.d("PhotoPicker", "No media selected")
                    Snackbar.make(binding.root, "No images selected.", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimelineEditorBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            // Setup the AuthStateListener for the fragment
            authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    db = mainActivity.db
                    storage = FirebaseStorage.getInstance()
                    userId = mainActivity.userId
                    appId = mainActivity.appId
                    // Only setup/listen if MainActivity confirms Firebase is ready and user ID is available
                    if (mainActivity.isAuthReady && userId != null) {
                        parentTrip = args.trip // Get the parent trip from Safe Args
                        currentTimelineItem = args.timelineItem // Get the timeline item if editing
                        loadTimelineItemData() // Load data if editing
                        setupDateAndTimePickers()
                        setupListeners()
                    } else {
                        Log.e("TimelineEditorFragment", "Auth state changed, but MainActivity's Firebase/User ID not fully ready yet. Waiting...")
                    }
                } else {
                    Log.d("TimelineEditorFragment", "User signed out or not authenticated.")
                    Snackbar.make(binding.root, "Please sign in to add timeline items.", Snackbar.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
            }
            mainActivity.auth.addAuthStateListener(authStateListener!!)
        } else {
            Log.e("TimelineEditorFragment", "MainActivity is null, cannot access Firebase services.")
            Snackbar.make(binding.root, "App initialization error. Please restart.", Snackbar.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
        // Initially hide progress bar
        binding.pbImageUploadTimeline.visibility = View.GONE
    }

    private fun loadTimelineItemData() {
        currentTimelineItem?.let { item ->
            binding.tvTimelineEditorTitle.text = getString(R.string.edit_timeline_item)
            binding.btnTimelineEditorSave.text = getString(R.string.save_changes)

            // Populate fields
            if (item.date.isNotBlank()) {
                try {
                    val date = dateFormatter.parse(item.date)
                    binding.etDate.setText(displayDateFormatter.format(date!!))
                } catch (e: ParseException) {
                    Log.w("TimelineEditorFragment", "Error parsing timeline item date: ${item.date}", e)
                    binding.etDate.setText(item.date)
                }
            }
            if (item.time.isNotBlank()) {
                try {
                    val time = SimpleDateFormat("HH:mm", Locale.US).parse(item.time)
                    binding.etTime.setText(displayTimeFormatter.format(time!!))
                } catch (e: ParseException) {
                    Log.w("TimelineEditorFragment", "Error parsing timeline item time: ${item.time}", e)
                    binding.etTime.setText(item.time)
                }
            }
            binding.etDescription.setText(item.description)
            binding.etNotesEditor.setText(item.notes)
            // Load existing images if available
            selectedImageUrls.clear() // Clear any previous selections
            selectedImageUrls.addAll(item.imageUrls)
            displaySelectedImages()
        } ?: run {
            // If it's a new item, pre-fill date if passed from TripTimelineFragment
            binding.tvTimelineEditorTitle.text = getString(R.string.add_timeline_item)
            binding.btnTimelineEditorSave.text = getString(R.string.add_item)
            args.selectedDate?.let {
                try {
                    val date = dateFormatter.parse(it)
                    binding.etDate.setText(displayDateFormatter.format(date!!))
                } catch (e: ParseException) {
                    Log.w("TimelineEditorFragment", "Error parsing selected date argument: $it", e)
                }
            }
        }
    }

    // Displays selected images in the horizontal scroll view
    private fun displaySelectedImages() {
        binding.llImagePreviewsTimeline.removeAllViews() // Clear existing previews
        if (selectedImageUrls.isNotEmpty()) {
            binding.svImagePreviewsTimeline.visibility = View.VISIBLE // Show the scroll view
            selectedImageUrls.forEach { imageUrl ->
                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        resources.getDimensionPixelSize(R.dimen.image_preview_width),
                        resources.getDimensionPixelSize(R.dimen.image_preview_height)
                    ).also {
                        it.marginEnd = resources.getDimensionPixelSize(R.dimen.image_preview_margin)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
                }
                Glide.with(this)
                    .load(imageUrl)
                    .centerCrop()
                    .into(imageView)
                binding.llImagePreviewsTimeline.addView(imageView)
            }
            binding.btnClearImagesTimeline.visibility = View.VISIBLE // Show clear button if images are present
        } else {
            binding.svImagePreviewsTimeline.visibility = View.GONE // Hide the scroll view if no images
            binding.btnClearImagesTimeline.visibility = View.GONE // Hide clear button if no images
        }
    }

    private fun setupDateAndTimePickers() {
        binding.etDate.setOnClickListener { showDatePicker() }
        binding.etDate.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDatePicker() }

        binding.etTime.setOnClickListener { showTimePicker() }
        binding.etTime.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showTimePicker() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val initialDateString = binding.etDate.text.toString()
        if (initialDateString.isNotBlank()) {
            try {
                val date = displayDateFormatter.parse(initialDateString)
                calendar.time = date!!
            } catch (e: ParseException) {
                Log.w("TimelineEditorFragment", "Error parsing initial date for picker: $initialDateString", e)
            }
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDayOfMonth)
                }
                binding.etDate.setText(displayDateFormatter.format(selectedCalendar.time))
            },
            year, month, day
        ).show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val initialTimeString = binding.etTime.text.toString()
        if (initialTimeString.isNotBlank()) {
            try {
                val time = displayTimeFormatter.parse(initialTimeString)
                calendar.time = time!!
            } catch (e: ParseException) {
                Log.w("TimelineEditorFragment", "Error parsing initial time for picker: $initialTimeString", e)
            }
        }

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                }
                binding.etTime.setText(displayTimeFormatter.format(selectedCalendar.time))
            },
            hour, minute, false // false for 12-hour format, true for 24-hour format
        ).show()
    }

    private fun setupListeners() {
        binding.btnTimelineEditorCancel.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnTimelineEditorSave.setOnClickListener {
            saveTimelineItem()
        }
        binding.btnAddImagesTimeline.setOnClickListener {
            pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        binding.btnClearImagesTimeline.setOnClickListener {
            selectedImageUrls.clear()
            displaySelectedImages()
            Snackbar.make(binding.root, "Images cleared.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun uploadImagesToFirebaseStorage(imageUris: List<Uri>) {
        userId?.let { uid ->
            val timelineItemId = currentTimelineItem?.id ?: UUID.randomUUID().toString() // Use existing ID or generate new
            var uploadsCompleted = 0
            val totalUploads = imageUris.size

            binding.pbImageUploadTimeline.visibility = View.VISIBLE // Show progress bar
            binding.btnAddImagesTimeline.isEnabled = false // Disable add button during upload
            binding.btnClearImagesTimeline.isEnabled = false // Disable clear button during upload

            // Clear previously selected images to replace them with new selection
            selectedImageUrls.clear()

            imageUris.forEach { uri ->
                val fileName = "timeline_${UUID.randomUUID()}.jpg" // Unique file name for each image
                val imageRef: StorageReference = storage.reference
                    .child("images")
                    .child("users")
                    .child(uid)
                    .child("timeline_items")
                    .child(timelineItemId)
                    .child(fileName)

                imageRef.putFile(uri)
                    .addOnSuccessListener { taskSnapshot ->
                        imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            selectedImageUrls.add(downloadUri.toString()) // Add to the list
                            uploadsCompleted++
                            if (uploadsCompleted == totalUploads) {
                                // All uploads complete
                                displaySelectedImages()
                                binding.pbImageUploadTimeline.visibility = View.GONE
                                binding.btnAddImagesTimeline.isEnabled = true
                                binding.btnClearImagesTimeline.isEnabled = true
                                Snackbar.make(binding.root, "All images uploaded!", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("TimelineEditorFragment", "Image upload failed for $fileName", e)
                        uploadsCompleted++ // Still increment to mark this upload attempt as done
                        if (uploadsCompleted == totalUploads) {
                            // Even if some failed, finish the process
                            binding.pbImageUploadTimeline.visibility = View.GONE
                            binding.btnAddImagesTimeline.isEnabled = true
                            binding.btnClearImagesTimeline.isEnabled = true
                            Snackbar.make(binding.root, "Some images failed to upload.", Snackbar.LENGTH_LONG).show()
                        }
                    }
            }
        } ?: run {
            Snackbar.make(binding.root, "Authentication error: User not logged in.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun saveTimelineItem() {
        userId?.let { uid ->
            val dateDisplay = binding.etDate.text.toString().trim()
            val timeDisplay = binding.etTime.text.toString().trim()
            val description = binding.etDescription.text.toString().trim()
            val notes = binding.etNotesEditor.text.toString().trim()

            if (dateDisplay.isEmpty() || timeDisplay.isEmpty() || description.isEmpty()) {
                Snackbar.make(binding.root, "Please fill Date, Time, and Description.", Snackbar.LENGTH_LONG).show()
                return
            }

            // Convert display dates/times to storage format
            val date = try {
                dateFormatter.format(displayDateFormatter.parse(dateDisplay)!!)
            } catch (e: ParseException) {
                Snackbar.make(binding.root, "Invalid Date format.", Snackbar.LENGTH_LONG).show()
                return
            }
            val time = try {
                // Using a 24-hour format for internal storage for easier sorting/comparison
                SimpleDateFormat("HH:mm", Locale.US).format(displayTimeFormatter.parse(timeDisplay)!!)
            } catch (e: ParseException) {
                Snackbar.make(binding.root, "Invalid Time format.", Snackbar.LENGTH_LONG).show()
                return
            }

            val itemToSave = currentTimelineItem?.copy(
                tripId = parentTrip.id,
                date = date,
                time = time,
                description = description,
                notes = notes,
                imageUrls = selectedImageUrls.toList()
            ) ?: TimelineItem(
                tripId = parentTrip.id,
                date = date,
                time = time,
                description = description,
                notes = notes,
                imageUrls = selectedImageUrls.toList()
            )

            val timelineCollectionRef = db.collection("artifacts").document(appId)
                .collection("users").document(uid)
                .collection("trips").document(parentTrip.id)
                .collection("timeline")

            if (currentTimelineItem == null) {
                // Add new timeline item
                timelineCollectionRef.add(itemToSave)
                    .addOnSuccessListener { documentReference ->
                        Snackbar.make(binding.root, "Timeline item added!", Snackbar.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    .addOnFailureListener { e ->
                        Log.w("TimelineEditorFragment", "Error adding timeline item", e)
                        Snackbar.make(binding.root, "Error adding timeline item: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
            } else {
                // Update existing timeline item
                timelineCollectionRef.document(itemToSave.id).set(itemToSave)
                    .addOnSuccessListener {
                        Snackbar.make(binding.root, "Timeline item updated!", Snackbar.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    .addOnFailureListener { e ->
                        Log.w("TimelineEditorFragment", "Error updating timeline item", e)
                        Snackbar.make(binding.root, "Error updating timeline item: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
            }
        } ?: run {
            Log.e("TimelineEditorFragment", "User ID is null, cannot save timeline item.")
            Snackbar.make(binding.root, "Authentication error. Cannot save timeline item.", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        authStateListener?.let { (activity as? MainActivity)?.auth?.removeAuthStateListener(it) }
        (activity as? MainActivity)?.setBottomNavigationVisibility(true)
    }
}