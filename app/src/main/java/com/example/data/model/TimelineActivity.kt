package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class ActivityType {
    VISITED_PLACE,
    ADDED_MEMORY,
    FOLLOWED_USER,
    FOLLOWER_GAINED,
    LIKED_MEMORY,
    COMMENTED_MEMORY
}

data class TimelineActivity(
    @DocumentId
    val id: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("userName")
    val userName: String = "",
    @PropertyName("userPhoto")
    val userPhoto: String = "",
    @PropertyName("type")
    val type: String = ActivityType.ADDED_MEMORY.name,
    @PropertyName("title")
    val title: String = "",
    @PropertyName("description")
    val description: String = "",
    @PropertyName("placeName")
    val placeName: String = "",
    @PropertyName("memoryId")
    val memoryId: String = "",
    @PropertyName("memoryPhoto")
    val memoryPhoto: String = "",
    @PropertyName("targetUserId")
    val targetUserId: String = "",
    @PropertyName("targetUserName")
    val targetUserName: String = "",
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Date? = null
)
