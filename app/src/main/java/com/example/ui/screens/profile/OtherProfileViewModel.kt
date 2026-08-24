package com.example.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FollowStatus
import com.example.data.model.Memory
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OtherProfileUiState(
    val targetUser: UserProfile? = null,
    val followStatus: FollowStatus = FollowStatus.NONE,
    val memories: List<Memory> = emptyList(),
    val currentUserProfile: UserProfile? = null,
    val isLoading: Boolean = true,
    val isFollowActionInProgress: Boolean = false,
    val isStartingChat: Boolean = false,
    val isBlocked: Boolean = false,
    val actionMessage: String? = null
)

class OtherProfileViewModel(
    private val targetUserId: String,
    private val userRepository: UserRepository = UserRepository(),
    private val memoryRepository: MemoryRepository = MemoryRepository(),
    private val chatRepository: ChatRepository = ChatRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtherProfileUiState())
    val uiState: StateFlow<OtherProfileUiState> = _uiState.asStateFlow()

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
                userRepository.getRelationshipStatus(currentUid, targetUserId).collect { status ->
                    _uiState.update { it.copy(followStatus = status) }
                }
            }
        }

        viewModelScope.launch {
            userRepository.getUserProfile(targetUserId).collect { target ->
                _uiState.update { it.copy(targetUser = target, isLoading = false) }
            }
        }

        viewModelScope.launch {
            memoryRepository.getPublicUserMemories(targetUserId).collect { list ->
                _uiState.update { it.copy(memories = list) }
            }
        }
    }

    fun toggleFollow() {
        val current = _uiState.value.currentUserProfile ?: return
        val target = _uiState.value.targetUser ?: return
        val status = _uiState.value.followStatus

        _uiState.update { it.copy(isFollowActionInProgress = true) }
        viewModelScope.launch {
            when (status) {
                FollowStatus.FOLLOWING -> {
                    userRepository.unfollowUser(current.uid, target.uid)
                }
                FollowStatus.REQUESTED -> {
                    userRepository.cancelFollowRequest(current.uid, target.uid)
                }
                FollowStatus.NONE -> {
                    if (target.isPrivateAccount) {
                        userRepository.requestFollow(current, target)
                    } else {
                        userRepository.followUser(current, target)
                    }
                }
                FollowStatus.BLOCKED -> {}
            }
            _uiState.update { it.copy(isFollowActionInProgress = false) }
        }
    }

    fun blockUser(onSuccess: () -> Unit) {
        val currentUid = authRepository.currentUserId
        if (currentUid.isBlank() || targetUserId.isBlank()) return
        viewModelScope.launch {
            userRepository.blockUser(currentUid, targetUserId)
            _uiState.update { it.copy(isBlocked = true, actionMessage = "User blocked successfully.") }
            onSuccess()
        }
    }

    fun reportUser(reason: String) {
        val currentUid = authRepository.currentUserId
        if (currentUid.isBlank() || targetUserId.isBlank()) return
        viewModelScope.launch {
            userRepository.reportUser(currentUid, targetUserId, reason)
            _uiState.update { it.copy(actionMessage = "Thank you. Report submitted for review.") }
        }
    }

    fun clearActionMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun startChat(onChatReady: (chatId: String, recipientId: String) -> Unit) {
        val currentUid = authRepository.currentUserId
        if (currentUid.isBlank() || targetUserId.isBlank()) return

        _uiState.update { it.copy(isStartingChat = true) }
        viewModelScope.launch {
            val result = chatRepository.getOrCreateChat(currentUid, targetUserId)
            result.fold(
                onSuccess = { chatId ->
                    _uiState.update { it.copy(isStartingChat = false) }
                    onChatReady(chatId, targetUserId)
                },
                onFailure = {
                    _uiState.update { it.copy(isStartingChat = false) }
                }
            )
        }
    }
}

