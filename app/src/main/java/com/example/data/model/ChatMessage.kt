package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ChatMessage(
    @DocumentId
    val id: String = "",
    @PropertyName("chatId")
    val chatId: String = "",
    @PropertyName("senderId")
    val senderId: String = "",
    @PropertyName("recipientId")
    val recipientId: String = "",
    @PropertyName("ciphertext")
    val ciphertext: String = "", // E2EE encrypted ciphertext (Base64)
    @PropertyName("nonce")
    val nonce: String = "", // AES-GCM 12-byte IV (Base64)
    @PropertyName("isPhoto")
    val isPhoto: Boolean = false,
    @PropertyName("encryptedPhotoUrl")
    val encryptedPhotoUrl: String = "", // Cloud Storage URL to encrypted ciphertext blob
    @PropertyName("encryptedPhotoKey")
    val encryptedPhotoKey: String = "", // Encrypted symmetric key (Base64)
    @PropertyName("status")
    val status: String = "sent", // "sent", "delivered", "read"
    @ServerTimestamp
    @PropertyName("timestamp")
    val timestamp: Date? = null
)
