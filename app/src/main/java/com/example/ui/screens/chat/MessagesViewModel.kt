package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.CryptoManager
import com.example.data.model.Chat
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatItemUi(
    val chat: Chat,
    val otherUser: UserProfile?,
    val lastMessagePreview: String,
    val unreadCount: Long
)

data class MessagesUiState(
    val chats: List<ChatItemUi> = emptyList(),
    val followingUsers: List<UserProfile> = emptyList(),
    val isLoading: Boolean = true,
    val showNewChatDialog: Boolean = false
)

class MessagesViewModel(
    private val chatRepository: ChatRepository = ChatRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    val currentUserId: String
        get() = authRepository.currentUserId

    init {
        loadChats()
        loadFollowingUsers()
    }

    private fun loadChats() {
        val currentUid = authRepository.currentUserId
        if (currentUid.isBlank()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            chatRepository.getUserChats(currentUid).collect { chatList ->
                val chatItemUis = chatList.map { chat ->
                    val otherUserId = chat.participants.firstOrNull { it != currentUid } ?: ""
                    var otherUser: UserProfile? = null
                    if (otherUserId.isNotBlank()) {
                        userRepository.getUserProfile(otherUserId).collect { profile ->
                            otherUser = profile
                        }
                    }

                    // Decrypt last message preview if present
                    val preview = when {
                        chat.isPhotoMessage -> "📷 Encrypted Photo"
                        chat.lastMessageCiphertext.isNotBlank() && chat.lastMessageNonce.isNotBlank() && otherUser != null -> {
                            val decrypted = CryptoManager.decryptMessage(
                                ciphertextBase64 = chat.lastMessageCiphertext,
                                nonceBase64 = chat.lastMessageNonce,
                                senderPublicKeyBase64 = otherUser?.publicKey ?: ""
                            )
                            decrypted ?: "🔒 Encrypted message"
                        }
                        chat.lastMessageCiphertext.isNotBlank() -> "🔒 Encrypted message"
                        else -> "No messages yet"
                    }

                    val unread = chat.unreadCounts[currentUid] ?: 0L

                    ChatItemUi(
                        chat = chat,
                        otherUser = otherUser,
                        lastMessagePreview = preview,
                        unreadCount = unread
                    )
                }
                _uiState.update { it.copy(chats = chatItemUis, isLoading = false) }
            }
        }
    }

    private fun loadFollowingUsers() {
        val currentUid = authRepository.currentUserId
        if (currentUid.isBlank()) return

        viewModelScope.launch {
            userRepository.getUserProfile(currentUid).collect { profile ->
                if (profile != null && profile.following.isNotEmpty()) {
                    val users = mutableListOf<UserProfile>()
                    profile.following.forEach { followedId ->
                        launch {
                            userRepository.getUserProfile(followedId).collect { followedUser ->
                                if (followedUser != null) {
                                    users.removeAll { it.uid == followedUser.uid }
                                    users.add(followedUser)
                                    _uiState.update { it.copy(followingUsers = users.toList()) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun setShowNewChatDialog(show: Boolean) {
        _uiState.update { it.copy(showNewChatDialog = show) }
    }

    fun startChatWithUser(targetUserId: String, onChatReady: (String, String) -> Unit) {
        val currentUid = authRepository.currentUserId
        if (currentUid.isBlank() || targetUserId.isBlank()) return

        viewModelScope.launch {
            val result = chatRepository.getOrCreateChat(currentUid, targetUserId)
            result.onSuccess { chatId ->
                _uiState.update { it.copy(showNewChatDialog = false) }
                onChatReady(chatId, targetUserId)
            }
        }
    }
}
