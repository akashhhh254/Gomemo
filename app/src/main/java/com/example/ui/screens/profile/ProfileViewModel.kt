package com.example.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

enum class ProfileTab {
    MEMORIES,
    SAVED,
    TIMELINE
}

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val memories: List<Memory> = emptyList(),
    val savedMemories: List<Memory> = emptyList(),
    val activeTab: ProfileTab = ProfileTab.MEMORIES,
    val isGridMode: Boolean = true,
    val isLoading: Boolean = true,
    val isUploadingPhoto: Boolean = false,
    val isSavingSettings: Boolean = false,
    val isClearingLocationHistory: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val memoryRepository: MemoryRepository = MemoryRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        val currentUid = authRepository.currentUserId
        if (currentUid.isNotBlank()) {
            viewModelScope.launch {
                userRepository.getUserProfile(currentUid).collect { profile ->
                    _uiState.update { it.copy(userProfile = profile, isLoading = false) }
                    loadSavedMemories(profile?.savedMemories ?: emptyList())
                }
            }
            viewModelScope.launch {
                memoryRepository.getUserMemories(currentUid).collect { memoriesList ->
                    _uiState.update { it.copy(memories = memoriesList) }
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadSavedMemories(savedIds: List<String>) {
        if (savedIds.isEmpty()) {
            _uiState.update { it.copy(savedMemories = emptyList()) }
            return
        }

        viewModelScope.launch {
            memoryRepository.getMemoriesByIds(savedIds).collect { savedList ->
                _uiState.update { it.copy(savedMemories = savedList) }
            }
        }
    }

    fun setTab(tab: ProfileTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setGridMode(grid: Boolean) {
        _uiState.update { it.copy(isGridMode = grid) }
    }

    fun uploadProfilePhoto(context: Context, uri: Uri) {
        val uid = authRepository.currentUserId
        if (uid.isBlank()) return
        _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val result = userRepository.uploadProfilePhoto(uid, inputStream)
                    result.fold(
                        onSuccess = {
                            _uiState.update { it.copy(isUploadingPhoto = false, message = "Profile photo updated!") }
                        },
                        onFailure = { err ->
                            _uiState.update { it.copy(isUploadingPhoto = false, errorMessage = "Failed to upload photo: ${err.localizedMessage}") }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploadingPhoto = false, errorMessage = "Error: ${e.localizedMessage}") }
            }
        }
    }

    fun updateProfileInfo(fullName: String, username: String, bio: String, onSuccess: () -> Unit) {
        val uid = authRepository.currentUserId
        if (uid.isBlank()) return
        _uiState.update { it.copy(isSavingSettings = true, errorMessage = null) }
        viewModelScope.launch {
            val result = userRepository.updateProfile(uid, fullName, username, bio)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSavingSettings = false, message = "Profile updated!") }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSavingSettings = false, errorMessage = "Failed to update profile: ${err.localizedMessage}") }
                }
            )
        }
    }

    fun updatePrivacySettings(locationTracking: Boolean, isPrivate: Boolean) {
        val uid = authRepository.currentUserId
        if (uid.isBlank()) return
        viewModelScope.launch {
            userRepository.updatePrivacySettings(uid, locationTracking, isPrivate)
        }
    }

    fun deleteLocationHistory() {
        val uid = authRepository.currentUserId
        if (uid.isBlank()) return
        _uiState.update { it.copy(isClearingLocationHistory = true) }
        viewModelScope.launch {
            val result = memoryRepository.deleteUserLocationHistory(uid)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isClearingLocationHistory = false, message = "Location history deleted.") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isClearingLocationHistory = false, errorMessage = "Failed to clear location: ${err.localizedMessage}") }
                }
            )
        }
    }

    fun signOut(onLoggedOut: () -> Unit) {
        authRepository.signOut()
        onLoggedOut()
    }
}
