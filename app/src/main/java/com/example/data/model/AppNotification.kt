package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class NotificationType {
    NEW_FOLLOWER,
    LIKE,
    COMMENT
}

data class AppNotification(
    @DocumentId
    val id: String = "",
    @PropertyName("recipientId")
    val recipientId: String = "",
    @PropertyName("senderId")
    val senderId: String = "",
    @PropertyName("senderName")
    val senderName: String = "",
    @PropertyName("senderUsername")
    val senderUsername: String = "",
    @PropertyName("senderPhoto")
    val senderPhoto: String = "",
    @PropertyName("type")
    val type: String = NotificationType.LIKE.name,
    @PropertyName("memoryId")
    val memoryId: String = "",
    @PropertyName("memoryPhoto")
    val memoryPhoto: String = "",
    @PropertyName("text")
    val text: String = "",
    @PropertyName("read")
    val read: Boolean = false,
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Date? = null
)
