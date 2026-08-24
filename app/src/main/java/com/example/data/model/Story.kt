package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Story(
    @DocumentId
    val id: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("userName")
    val userName: String = "",
    @PropertyName("userUsername")
    val userUsername: String = "",
    @PropertyName("userPhoto")
    val userPhoto: String = "",
    @PropertyName("photoUrl")
    val photoUrl: String = "",
    @PropertyName("placeName")
    val placeName: String = "",
    @PropertyName("caption")
    val caption: String = "",
    @PropertyName("viewers")
    val viewers: List<String> = emptyList(),
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Date? = null,
    @PropertyName("expiresAt")
    val expiresAt: Date? = null
)
