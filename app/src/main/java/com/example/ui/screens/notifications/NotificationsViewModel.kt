package com.example.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.FollowRequest
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    val pendingFollowRequests: List<FollowRequest> = emptyList(),
    val currentUserProfile: UserProfile? = null,
    val isLoading: Boolean = true
)

class NotificationsViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val currentUid = authRepository.currentUserId
        if (currentUid.isNotBlank()) {
            viewModelScope.launch {
                userRepository.getUserProfile(currentUid).collect { profile ->
                    _uiState.update { it.copy(currentUserProfile = profile) }
                }
            }

            viewModelScope.launch {
                userRepository.getNotifications(currentUid).collect { notifs ->
                    _uiState.update { it.copy(notifications = notifs, isLoading = false) }
                }
            }

            viewModelScope.launch {
                userRepository.getPendingFollowRequests(currentUid).collect { requests ->
                    _uiState.update { it.copy(pendingFollowRequests = requests) }
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            userRepository.markNotificationAsRead(notificationId)
        }
    }

    fun acceptFollowRequest(request: FollowRequest) {
        val targetProfile = _uiState.value.currentUserProfile ?: return
        viewModelScope.launch {
            val requesterProfile = UserProfile(
                uid = request.fromUserId,
                username = request.fromUserUsername,
                fullName = request.fromUserName,
                profilePhoto = request.fromUserPhoto
            )
            userRepository.acceptFollowRequest(requesterProfile, targetProfile)
        }
    }

    fun declineFollowRequest(request: FollowRequest) {
        val currentUid = authRepository.currentUserId
        if (currentUid.isBlank()) return
        viewModelScope.launch {
            userRepository.declineFollowRequest(request.fromUserId, currentUid)
        }
    }
}

