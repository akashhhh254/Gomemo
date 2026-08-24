package com.example.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Comment
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

data class MemoryDetailsUiState(
    val memory: Memory? = null,
    val comments: List<Comment> = emptyList(),
    val isLiked: Boolean = false,
    val currentUserProfile: UserProfile? = null,
    val newCommentText: String = "",
    val isSendingComment: Boolean = false,
    val isDeleting: Boolean = false,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false
)

class MemoryDetailsViewModel(
    private val memoryId: String,
    private val memoryRepository: MemoryRepository = MemoryRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryDetailsUiState())
    val uiState: StateFlow<MemoryDetailsUiState> = _uiState.asStateFlow()

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
                memoryRepository.isMemoryLikedByUser(memoryId, currentUid).collect { liked ->
                    _uiState.update { it.copy(isLiked = liked) }
                }
            }
        }

        viewModelScope.launch {
            memoryRepository.getMemoryById(memoryId).collect { mem ->
                _uiState.update { it.copy(memory = mem, isLoading = false) }
            }
        }

        viewModelScope.launch {
            memoryRepository.getComments(memoryId).collect { commentList ->
                _uiState.update { it.copy(comments = commentList) }
            }
        }
    }

    fun onCommentTextChange(text: String) {
        _uiState.update { it.copy(newCommentText = text) }
    }

    fun toggleLike() {
        val mem = _uiState.value.memory ?: return
        val currentUid = authRepository.currentUserId
        val user = _uiState.value.currentUserProfile ?: UserProfile(uid = currentUid, fullName = "GoMemo Explorer")
        viewModelScope.launch {
            memoryRepository.toggleLike(
                memory = mem,
                userId = user.uid,
                userName = user.fullName,
                userPhoto = user.profilePhoto
            )
        }
    }

    fun addComment() {
        val text = _uiState.value.newCommentText.trim()
        if (text.isBlank()) return
        val mem = _uiState.value.memory ?: return
        val currentUid = authRepository.currentUserId
        val user = _uiState.value.currentUserProfile ?: UserProfile(uid = currentUid, fullName = "GoMemo Explorer")

        _uiState.update { it.copy(isSendingComment = true) }
        viewModelScope.launch {
            memoryRepository.addComment(
                memory = mem,
                userId = user.uid,
                userName = user.fullName,
                userUsername = user.username,
                userPhoto = user.profilePhoto,
                text = text
            )
            _uiState.update { it.copy(newCommentText = "", isSendingComment = false) }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            memoryRepository.deleteComment(memoryId, commentId)
        }
    }

    fun deleteMemory(onSuccess: () -> Unit) {
        val mem = _uiState.value.memory ?: return
        val currentUid = authRepository.currentUserId.ifBlank { mem.userId }
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            val result = memoryRepository.deleteMemory(mem.id, currentUid)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isDeleting = false, isDeleted = true) }
                    onSuccess()
                },
                onFailure = {
                    _uiState.update { it.copy(isDeleting = false) }
                }
            )
        }
    }
}
