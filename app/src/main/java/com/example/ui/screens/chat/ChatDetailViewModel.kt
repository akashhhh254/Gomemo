package com.example.ui.screens.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.CryptoManager
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DecryptedMessageUi(
    val id: String,
    val senderId: String,
    val isMine: Boolean,
    val isPhoto: Boolean,
    val text: String,
    val photoBitmap: Bitmap? = null,
    val isDecryptingPhoto: Boolean = false,
    val timestamp: String = ""
)

data class ChatDetailUiState(
    val chatId: String = "",
    val recipientUser: UserProfile? = null,
    val messages: List<DecryptedMessageUi> = emptyList(),
    val inputMessage: String = "",
    val isSending: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class ChatDetailViewModel(
    private val chatId: String,
    private val recipientUserId: String,
    private val chatRepository: ChatRepository = ChatRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatDetailUiState(chatId = chatId))
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    private val currentUserId: String = authRepository.currentUserId
    private val decryptedPhotosCache = mutableMapOf<String, Bitmap>()

    init {
        loadRecipientAndMessages()
        markAsRead()
    }

    private fun markAsRead() {
        if (chatId.isNotBlank() && currentUserId.isNotBlank()) {
            viewModelScope.launch {
                chatRepository.markChatAsRead(chatId, currentUserId)
            }
        }
    }

    private fun loadRecipientAndMessages() {
        if (recipientUserId.isNotBlank()) {
            viewModelScope.launch {
                userRepository.getUserProfile(recipientUserId).collect { profile ->
                    _uiState.update { it.copy(recipientUser = profile) }
                }
            }
        }

        if (chatId.isNotBlank()) {
            viewModelScope.launch {
                chatRepository.getChatMessages(chatId).collect { rawMessages ->
                    val recipientKey = _uiState.value.recipientUser?.publicKey ?: ""
                    val uiMessages = rawMessages.map { raw ->
                        val isMine = raw.senderId == currentUserId
                        val text = if (!raw.isPhoto) {
                            if (raw.ciphertext.isNotBlank() && raw.nonce.isNotBlank()) {
                                val keyToUse = if (isMine) {
                                    // For messages I sent, sender was me, recipient was other.
                                    recipientKey
                                } else {
                                    // For messages received, sender was other user
                                    recipientKey
                                }
                                val dec = CryptoManager.decryptMessage(
                                    ciphertextBase64 = raw.ciphertext,
                                    nonceBase64 = raw.nonce,
                                    senderPublicKeyBase64 = keyToUse
                                )
                                dec ?: "[Encrypted message]"
                            } else {
                                raw.ciphertext
                            }
                        } else {
                            ""
                        }

                        val cachedBitmap = decryptedPhotosCache[raw.id]

                        DecryptedMessageUi(
                            id = raw.id,
                            senderId = raw.senderId,
                            isMine = isMine,
                            isPhoto = raw.isPhoto,
                            text = text,
                            photoBitmap = cachedBitmap,
                            isDecryptingPhoto = raw.isPhoto && cachedBitmap == null && raw.encryptedPhotoUrl.isNotBlank()
                        )
                    }

                    _uiState.update { it.copy(messages = uiMessages, isLoading = false) }

                    // Decrypt photos that haven't been decrypted yet
                    rawMessages.filter { it.isPhoto && it.encryptedPhotoUrl.isNotBlank() && !decryptedPhotosCache.containsKey(it.id) }.forEach { photoMsg ->
                        decryptPhotoMessage(photoMsg)
                    }
                }
            }
        }
    }

    private fun decryptPhotoMessage(msg: ChatMessage) {
        viewModelScope.launch {
            val senderKey = _uiState.value.recipientUser?.publicKey ?: ""
            if (senderKey.isNotBlank()) {
                val bitmap = chatRepository.downloadAndDecryptPhoto(
                    encryptedPhotoUrl = msg.encryptedPhotoUrl,
                    encryptedPhotoKey = msg.encryptedPhotoKey,
                    nonce = msg.nonce,
                    senderPublicKey = senderKey
                )
                if (bitmap != null) {
                    decryptedPhotosCache[msg.id] = bitmap
                    _uiState.update { current ->
                        val updated = current.messages.map {
                            if (it.id == msg.id) it.copy(photoBitmap = bitmap, isDecryptingPhoto = false) else it
                        }
                        current.copy(messages = updated)
                    }
                }
            }
        }
    }

    fun onInputMessageChange(text: String) {
        _uiState.update { it.copy(inputMessage = text, errorMessage = null) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputMessage.trim()
        val recipient = _uiState.value.recipientUser ?: return
        if (text.isBlank() || recipient.publicKey.isBlank()) return

        _uiState.update { it.copy(isSending = true, inputMessage = "") }

        viewModelScope.launch {
            val result = chatRepository.sendTextMessage(
                chatId = chatId,
                senderId = currentUserId,
                recipientId = recipient.uid,
                recipientPublicKey = recipient.publicKey,
                plaintext = text
            )
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = "Send failed: ${error.localizedMessage}") }
            }
            _uiState.update { it.copy(isSending = false) }
        }
    }

    fun sendPhoto(context: Context, uri: Uri) {
        val recipient = _uiState.value.recipientUser ?: return
        if (recipient.publicKey.isBlank()) return

        _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Could not open image stream")

                val result = chatRepository.sendPrivatePhoto(
                    chatId = chatId,
                    senderId = currentUserId,
                    recipientId = recipient.uid,
                    recipientPublicKey = recipient.publicKey,
                    imageInputStream = stream
                )
                result.onFailure { error ->
                    _uiState.update { it.copy(errorMessage = "Photo send failed: ${error.localizedMessage}") }
                }
            } catch (e: Exception) {
                Log.e("ChatDetailVM", "Failed to send photo: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "Photo error: ${e.localizedMessage}") }
            } finally {
                _uiState.update { it.copy(isUploadingPhoto = false) }
            }
        }
    }
}
