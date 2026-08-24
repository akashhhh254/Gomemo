package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class FollowStatus {
    NONE,
    REQUESTED,
    FOLLOWING,
    BLOCKED
}

data class FollowRequest(
    @DocumentId
    val id: String = "",
    @PropertyName("fromUserId")
    val fromUserId: String = "",
    @PropertyName("fromUserName")
    val fromUserName: String = "",
    @PropertyName("fromUserUsername")
    val fromUserUsername: String = "",
    @PropertyName("fromUserPhoto")
    val fromUserPhoto: String = "",
    @PropertyName("toUserId")
    val toUserId: String = "",
    @PropertyName("status")
    val status: String = "pending", // "pending", "accepted", "declined"
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Date? = null
)
