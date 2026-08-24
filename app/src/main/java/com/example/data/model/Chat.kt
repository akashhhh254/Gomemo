package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Chat(
    @DocumentId
    val id: String = "",
    @PropertyName("participants")
    val participants: List<String> = emptyList(),
    @PropertyName("lastMessageCiphertext")
    val lastMessageCiphertext: String = "",
    @PropertyName("lastMessageNonce")
    val lastMessageNonce: String = "",
    @PropertyName("lastMessageSenderId")
    val lastMessageSenderId: String = "",
    @PropertyName("isPhotoMessage")
    val isPhotoMessage: Boolean = false,
    @PropertyName("unreadCounts")
    val unreadCounts: Map<String, Long> = emptyMap(),
    @ServerTimestamp
    @PropertyName("lastMessageTimestamp")
    val lastMessageTimestamp: Date? = null,
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Date? = null
)
