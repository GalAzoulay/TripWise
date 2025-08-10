package com.example.tripwise.ui.editor

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.tripwise.data.Trip
import com.example.tripwise.data.TripType
import com.example.tripwise.databinding.FragmentTripEditorBinding
import com.example.tripwise.ui.MainActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
import com.example.tripwise.data.User
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

class TripEditorFragment : Fragment() {

    private var _binding: FragmentTripEditorBinding? = null
    private val binding get() = _binding!!

    private val args: TripEditorFragmentArgs by navArgs()
    private var currentTrip: Trip? = null

    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var userId: String? = null
    var userProfile: User? = null
    private lateinit var appId: String

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    private val displayFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.US).apply { isLenient = false }

    private val selectedImageUrls: MutableList<String> = mutableListOf()

    private val pickImagesLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(PickMultipleVisualMedia(5)) { uris: List<Uri>? ->
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
        _binding = FragmentTripEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    db = mainActivity.db
                    storage = FirebaseStorage.getInstance()
                    userId = mainActivity.userId
                    appId = mainActivity.appId
                    if (mainActivity.isAuthReady && userId != null) {
                        loadTripData()
                        setupUI()
                        setupDatePickers()
                        setupListeners()
                    } else {
                        Log.e("TripEditorFragment", "Auth state changed, but MainActivity's Firebase/User ID not fully ready yet. Waiting...")
                    }
                } else {
                    Log.d("TripEditorFragment", "User signed out or not authenticated. Cannot load editor data.")
                    Snackbar.make(binding.root, "Please sign in to edit trips.", Snackbar.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
            }
            mainActivity.auth.addAuthStateListener(authStateListener!!)

        } else {
            Log.e("TripEditorFragment", "MainActivity is null, cannot access Firebase services.")
            Snackbar.make(binding.root, "App initialization error. Please restart.", Snackbar.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
        binding.pbImageUpload.visibility = View.GONE
    }


    private fun setupUI() {
        if (currentTrip == null) {
            binding.tvEditorTitle.text = getString(R.string.create_new_trip)
            binding.btnSave.text = getString(R.string.create)
        } else {
            binding.tvEditorTitle.text = getString(R.string.edit_trip)
            binding.btnSave.text = getString(R.string.save_changes)
        }
        binding.pbImageUpload.visibility = View.GONE
    }

    // Loads trip data if editing an existing trip.
    private fun loadTripData() {
        args.trip?.let { trip ->
            currentTrip = trip
            binding.etDestination.setText(trip.destination)
            if (trip.startDate.isNotBlank()) {
                val trimmedStartDate = trip.startDate.trim()
                Log.d("TripEditorFragment", "Attempting to parse startDate: '$trimmedStartDate' (length: ${trimmedStartDate.length})")
                try {
                    val date = dateFormatter.parse(trimmedStartDate)
                    binding.etStartDate.setText(displayFormatter.format(date!!))
                } catch (e: ParseException) {
                    Log.e("TripEditorFragment", "Stored start date '$trimmedStartDate' is unparseable. Clearing field.", e)
                    binding.etStartDate.setText("")
                    Snackbar.make(binding.root, "Warning: Start date for this trip was invalid and cleared. Please re-select.", Snackbar.LENGTH_LONG).show()
                }
            }
            if (trip.endDate.isNotBlank()) {
                val trimmedEndDate = trip.endDate.trim()
                Log.d("TripEditorFragment", "Attempting to parse endDate: '$trimmedEndDate' (length: ${trimmedEndDate.length})")
                try {
                    val date = dateFormatter.parse(trimmedEndDate)
                    binding.etEndDate.setText(displayFormatter.format(date!!))
                } catch (e: ParseException) {
                    Log.e("TripEditorFragment", "Stored end date '$trimmedEndDate' is unparseable. Clearing field.", e)
                    binding.etEndDate.setText("")
                    Snackbar.make(binding.root, "Warning: End date for this trip was invalid and cleared. Please re-select.", Snackbar.LENGTH_LONG).show()
                }
            }
            binding.etFlights.setText(trip.flights)
            binding.etAccommodation.setText(trip.accommodation)
            binding.etNotes.setText(trip.notes)

            selectedImageUrls.clear()
            selectedImageUrls.addAll(trip.imageUrls)
            displaySelectedImages()
        }
    }

    private fun displaySelectedImages() {
        binding.llImagePreviews.removeAllViews()
        if (selectedImageUrls.isNotEmpty()) {
            binding.svImagePreviews.visibility = View.VISIBLE
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
                binding.llImagePreviews.addView(imageView)
            }
            binding.btnClearImages.visibility = View.VISIBLE
        } else {
            binding.svImagePreviews.visibility = View.GONE
            binding.btnClearImages.visibility = View.GONE
        }
    }

    // Sets up DatePicker dialogs for start and end date EditText fields.
    private fun setupDatePickers() {
        binding.etStartDate.setOnClickListener { showDatePickerDialog(binding.etStartDate) }
        binding.etEndDate.setOnClickListener { showDatePickerDialog(binding.etEndDate) }
        binding.etStartDate.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showDatePickerDialog(binding.etStartDate)
        }
        binding.etEndDate.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showDatePickerDialog(binding.etEndDate)
        }
    }

    // Shows a DatePicker dialog and sets the selected date to the target EditText.
    private fun showDatePickerDialog(targetEditText: EditText) {
        val calendar = Calendar.getInstance()
        val initialDateString = targetEditText.text.toString()
        if (initialDateString.isNotBlank()) {
            try {
                val date = displayFormatter.parse(initialDateString)
                calendar.time = date!!
            } catch (e: ParseException) {
                Log.w("TripEditorFragment", "Could not parse initial date for picker: $initialDateString", e)
                try {
                    val date = dateFormatter.parse(initialDateString)
                    calendar.time = date!!
                } catch (e2: ParseException) {
                    Log.e("TripEditorFragment", "Could not parse initial date for picker: $initialDateString (storage format). Using current date.", e2)
                }
            }
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDayOfMonth)
                }
                targetEditText.setText(displayFormatter.format(selectedCalendar.time))
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSave.setOnClickListener {
            saveTrip()
        }

        binding.btnAddImages.setOnClickListener {
            pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        binding.btnClearImages.setOnClickListener {
            selectedImageUrls.clear()
            displaySelectedImages()
            Snackbar.make(binding.root, "Images cleared.", Snackbar.LENGTH_SHORT).show()
        }
    }

    // Uploads the selected images to Firebase Storage
    private fun uploadImagesToFirebaseStorage(imageUris: List<Uri>) {
        userId?.let { uid ->
            val tripId = currentTrip?.id ?: UUID.randomUUID().toString()
            var uploadsCompleted = 0
            val totalUploads = imageUris.size

            binding.pbImageUpload.visibility = View.VISIBLE
            binding.btnAddImages.isEnabled = false
            binding.btnClearImages.isEnabled = false

            selectedImageUrls.clear()

            imageUris.forEach { uri ->
                val fileName = "trip_${UUID.randomUUID()}.jpg"
                val imageRef: StorageReference = storage.reference
                    .child("images")
                    .child("users")
                    .child(uid)
                    .child("trips")
                    .child(tripId)
                    .child(fileName)

                imageRef.putFile(uri)
                    .addOnSuccessListener { taskSnapshot ->
                        imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            selectedImageUrls.add(downloadUri.toString())
                            uploadsCompleted++
                            if (uploadsCompleted == totalUploads) {
                                displaySelectedImages()
                                binding.pbImageUpload.visibility = View.GONE
                                binding.btnAddImages.isEnabled = true
                                binding.btnClearImages.isEnabled = true
                                Snackbar.make(binding.root, "All images uploaded!", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("TripEditorFragment", "Image upload failed for $fileName", e)
                        uploadsCompleted++
                        if (uploadsCompleted == totalUploads) {
                            binding.pbImageUpload.visibility = View.GONE
                            binding.btnAddImages.isEnabled = true
                            binding.btnClearImages.isEnabled = true
                            Snackbar.make(binding.root, "Some images failed to upload.", Snackbar.LENGTH_LONG).show()
                        }
                    }
            }
        } ?: run {
            Snackbar.make(binding.root, "Authentication error: User not logged in.", Snackbar.LENGTH_LONG).show()
        }
    }

    // Saves a new trip or updates an existing trip in Firestore.
    private fun saveTrip() {
        userId?.let { uid ->
            val destination = binding.etDestination.text.toString().trim()
            val startDateDisplay = binding.etStartDate.text.toString().trim()
            val endDateDisplay = binding.etEndDate.text.toString().trim()
            val flights = binding.etFlights.text.toString().trim()
            val accommodation = binding.etAccommodation.text.toString().trim()
            val notes = binding.etNotes.text.toString().trim()

            if (destination.isEmpty() || startDateDisplay.isEmpty() || endDateDisplay.isEmpty()) {
                Snackbar.make(binding.root, "Please fill in Destination, Start Date, and End Date.", Snackbar.LENGTH_LONG).show()
                return
            }

            // Convert display dates to underscored-MM-DD for storage
            val startDate = try {
                dateFormatter.format(displayFormatter.parse(startDateDisplay)!!)
            } catch (e: ParseException) {
                Snackbar.make(binding.root, "Invalid Start Date format.", Snackbar.LENGTH_LONG).show()
                return
            }
            val endDate = try {
                dateFormatter.format(displayFormatter.parse(endDateDisplay)!!)
            } catch (e: ParseException) {
                Snackbar.make(binding.root, "Invalid End Date format.", Snackbar.LENGTH_LONG).show()
                return
            }

            // Determine trip type based on dates
            val tripType = if (startDate >= dateFormatter.format(Date())) TripType.UPCOMING else TripType.PAST

            val tripToSave = currentTrip?.copy(
                destination = destination,
                startDate = startDate,
                endDate = endDate,
                type = tripType,
                userId = uid,
                flights = flights,
                accommodation = accommodation,
                notes = notes,
                imageUrls = selectedImageUrls.toList()
            ) ?: Trip( // Create new Trip if currentTrip is null
                destination = destination,
                startDate = startDate,
                endDate = endDate,
                type = tripType,
                userId = uid,
                flights = flights,
                accommodation = accommodation,
                notes = notes,
                imageUrls = selectedImageUrls.toList()
            )

            val tripsCollectionRef = db.collection("artifacts").document(appId)
                .collection("users").document(uid)
                .collection("trips")

            if (currentTrip == null) {
                // Add new trip
                tripsCollectionRef.add(tripToSave)
                    .addOnSuccessListener { documentReference ->
                        Snackbar.make(binding.root, "Trip to ${tripToSave.destination} created!", Snackbar.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    .addOnFailureListener { e ->
                        Log.w("TripEditorFragment", "Error adding document", e)
                        Snackbar.make(binding.root, "Error creating trip: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
            } else {
                // Update existing trip
                tripsCollectionRef.document(tripToSave.id).set(tripToSave)
                    .addOnSuccessListener {
                        Snackbar.make(binding.root, "Trip to ${tripToSave.destination} updated!", Snackbar.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    .addOnFailureListener { e ->
                        Log.w("TripEditorFragment", "Error updating document", e)
                        Snackbar.make(binding.root, "Error updating trip: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
            }
        } ?: run {
            Log.e("TripEditorFragment", "User ID is null, cannot save trip.")
            Snackbar.make(binding.root, "Authentication error. Cannot save trip.", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        authStateListener?.let { (activity as? MainActivity)?.auth?.removeAuthStateListener(it) }
        (activity as? MainActivity)?.setBottomNavigationVisibility(true)
    }
}