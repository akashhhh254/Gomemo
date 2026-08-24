package com.example.ui.screens.map

import android.content.Context
import android.location.Location
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

data class MapUiState(
    val memories: List<Memory> = emptyList(),
    val myMemoriesOnly: Boolean = false,
    val selectedMemory: Memory? = null,
    val userLocation: Location? = null,
    val centerLatitude: Double = 37.7749, // Default center (or user GPS)
    val centerLongitude: Double = -122.4194,
    val zoomLevel: Float = 12f,
    val searchQuery: String = "",
    val searchResults: List<PlaceLocation> = emptyList(),
    val isSearching: Boolean = false,
    val selectedCustomLocation: PlaceLocation? = null,
    val hasLocationPermission: Boolean = false,
    val currentUserProfile: UserProfile? = null,
    val isLoading: Boolean = true
)

class MapViewModel(
    private val memoryRepository: MemoryRepository = MemoryRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var locationHelper: LocationHelper? = null

    init {
        loadData()
    }

    fun initLocationHelper(context: Context) {
        if (locationHelper == null) {
            locationHelper = LocationHelper(context.applicationContext)
        }
    }

    private fun loadData() {
        val currentUid = authRepository.currentUser?.uid ?: ""
        if (currentUid.isNotBlank()) {
            viewModelScope.launch {
                userRepository.getUserProfile(currentUid).collect { profile ->
                    _uiState.update { it.copy(currentUserProfile = profile) }
                }
            }
        }

        viewModelScope.launch {
            memoryRepository.getPublicMemoriesFeed().collect { feed ->
                val validGeoMemories = feed.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                _uiState.update { current ->
                    current.copy(
                        memories = validGeoMemories,
                        isLoading = false,
                        // Center on the most recent memory if we have one and haven't centered yet
                        centerLatitude = validGeoMemories.firstOrNull()?.latitude ?: current.centerLatitude,
                        centerLongitude = validGeoMemories.firstOrNull()?.longitude ?: current.centerLongitude
                    )
                }
            }
        }
    }

    fun toggleFilterMyMemories() {
        _uiState.update { it.copy(myMemoriesOnly = !it.myMemoriesOnly) }
    }

    fun selectMemory(memory: Memory?) {
        _uiState.update {
            it.copy(
                selectedMemory = memory,
                centerLatitude = memory?.latitude ?: it.centerLatitude,
                centerLongitude = memory?.longitude ?: it.centerLongitude
            )
        }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (granted) {
            fetchUserLocation()
        }
    }

    fun fetchUserLocation() {
        viewModelScope.launch {
            val helper = locationHelper ?: return@launch
            val loc = helper.getCurrentLocation()
            if (loc != null) {
                _uiState.update {
                    it.copy(
                        userLocation = loc,
                        centerLatitude = loc.latitude,
                        centerLongitude = loc.longitude,
                        zoomLevel = 14f
                    )
                }
            }
        }
    }

    fun centerOnCoordinates(latitude: Double, longitude: Double, placeName: String = "") {
        _uiState.update {
            it.copy(
                centerLatitude = latitude,
                centerLongitude = longitude,
                zoomLevel = 15f
            )
        }
        val matching = _uiState.value.memories.firstOrNull {
            Math.abs(it.latitude - latitude) < 0.001 && Math.abs(it.longitude - longitude) < 0.001
        }
        if (matching != null) {
            selectMemory(matching)
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 3) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSearching = true) }
                val results = locationHelper?.searchLocations(query) ?: emptyList()
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    fun selectSearchResult(place: PlaceLocation) {
        _uiState.update {
            it.copy(
                centerLatitude = place.latitude,
                centerLongitude = place.longitude,
                searchQuery = "",
                searchResults = emptyList(),
                selectedCustomLocation = place
            )
        }
    }

    fun clearCustomLocation() {
        _uiState.update { it.copy(selectedCustomLocation = null) }
    }
}
