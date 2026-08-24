package com.example.ui.screens.add

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.location.LocationHelper
import com.example.data.location.PlaceLocation
import com.example.data.model.Memory
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AddMemoryUiState(
    val placeName: String = "",
    val locationName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val dateVisited: String = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
    val timeVisited: String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
    val selectedPhotoUris: List<Uri> = emptyList(),
    val caption: String = "",
    val tagsText: String = "",
    val visibility: String = "public", // "public" or "private"
    val isFetchingLocation: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgressMessage: String = "",
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val currentUserProfile: UserProfile? = null
)

class AddMemoryViewModel(
    private val memoryRepository: MemoryRepository = MemoryRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMemoryUiState())
    val uiState: StateFlow<AddMemoryUiState> = _uiState.asStateFlow()

    private var locationHelper: LocationHelper? = null

    init {
        loadUserProfile()
    }

    fun initLocationHelper(context: Context) {
        if (locationHelper == null) {
            locationHelper = LocationHelper(context.applicationContext)
        }
    }

    private fun loadUserProfile() {
        val currentUid = authRepository.currentUserId
        if (currentUid.isNotBlank()) {
            viewModelScope.launch {
                userRepository.getUserProfile(currentUid).collect { profile ->
                    _uiState.update { it.copy(currentUserProfile = profile) }
                }
            }
        }
    }

    fun setInitialLocation(lat: Double, lng: Double, placeName: String) {
        _uiState.update {
            it.copy(
                latitude = lat,
                longitude = lng,
                placeName = placeName,
                locationName = placeName
            )
        }
    }

    fun onPlaceNameChange(name: String) = _uiState.update { it.copy(placeName = name, errorMessage = null) }
    fun onLocationNameChange(loc: String) = _uiState.update { it.copy(locationName = loc) }
    fun onDateVisitedChange(date: String) = _uiState.update { it.copy(dateVisited = date) }
    fun onTimeVisitedChange(time: String) = _uiState.update { it.copy(timeVisited = time) }
    fun onCaptionChange(caption: String) = _uiState.update { it.copy(caption = caption, errorMessage = null) }
    fun onTagsChange(tags: String) = _uiState.update { it.copy(tagsText = tags) }
    fun onVisibilityChange(visibility: String) = _uiState.update { it.copy(visibility = visibility) }

    fun addPhotoUris(uris: List<Uri>) {
        _uiState.update { current ->
            val updated = (current.selectedPhotoUris + uris).distinct().take(6)
            current.copy(selectedPhotoUris = updated, errorMessage = null)
        }
    }

    fun removePhotoUri(uri: Uri) {
        _uiState.update { current ->
            current.copy(selectedPhotoUris = current.selectedPhotoUris.filter { it != uri })
        }
    }

    fun fetchCurrentLocation(context: Context) {
        initLocationHelper(context)
        _uiState.update { it.copy(isFetchingLocation = true) }
        viewModelScope.launch {
            val helper = locationHelper ?: return@launch
            val loc = helper.getCurrentLocation()
            if (loc != null) {
                val place = helper.getPlaceFromCoordinates(loc.latitude, loc.longitude)
                _uiState.update {
                    it.copy(
                        isFetchingLocation = false,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        placeName = if (it.placeName.isBlank()) place.placeName else it.placeName,
                        locationName = place.address
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isFetchingLocation = false,
                        errorMessage = "Location unavailable. You can enter the place name manually."
                    )
                }
            }
        }
    }

    fun saveMemory(context: Context, onSuccess: () -> Unit) {
        val state = _uiState.value
        val currentUid = authRepository.currentUserId.ifBlank { "user_explorer" }
        val profile = state.currentUserProfile ?: UserProfile(uid = currentUid, fullName = "GoMemo Explorer")

        if (state.placeName.trim().isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a place name for this memory.") }
            return
        }

        val tagsList = state.tagsText.split(" ", ",", "#")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        _uiState.update {
            it.copy(
                isUploading = true,
                uploadProgressMessage = "Uploading memory...",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val photoUrls = mutableListOf<String>()

            // Upload photos to Firebase Storage if any selected
            for ((index, uri) in state.selectedPhotoUris.withIndex()) {
                _uiState.update {
                    it.copy(uploadProgressMessage = "Uploading photo ${index + 1} of ${state.selectedPhotoUris.size}...")
                }
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val uploadResult = memoryRepository.uploadMemoryPhoto(currentUid, inputStream)
                        uploadResult.fold(
                            onSuccess = { url -> photoUrls.add(url) },
                            onFailure = { err ->
                                // Log upload error
                            }
                        )
                    }
                } catch (e: Exception) {
                    // Skip or handle
                }
            }

            _uiState.update { it.copy(uploadProgressMessage = "Saving memory to GoMemo...") }

            val memory = Memory(
                userId = currentUid,
                authorName = profile.fullName.ifBlank { "Explorer" },
                authorUsername = profile.username,
                authorPhoto = profile.profilePhoto,
                placeName = state.placeName.trim(),
                locationName = state.locationName.trim().ifBlank { state.placeName.trim() },
                latitude = state.latitude,
                longitude = state.longitude,
                dateVisited = state.dateVisited.trim(),
                timeVisited = state.timeVisited.trim(),
                photoUrls = photoUrls,
                caption = state.caption.trim(),
                tags = tagsList,
                visibility = state.visibility,
                likesCount = 0,
                commentsCount = 0
            )

            val createResult = memoryRepository.createMemory(
                memory = memory,
                authorName = profile.fullName.ifBlank { "Explorer" },
                authorPhoto = profile.profilePhoto
            )

            createResult.fold(
                onSuccess = {
                    _uiState.update { it.copy(isUploading = false, isSuccess = true) }
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            errorMessage = "Could not save memory: ${error.localizedMessage}"
                        )
                    }
                }
            )
        }
    }
}
