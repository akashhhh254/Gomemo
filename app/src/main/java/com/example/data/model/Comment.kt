package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Comment(
    @DocumentId
    val id: String = "",
    @PropertyName("memoryId")
    val memoryId: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("userName")
    val userName: String = "",
    @PropertyName("userUsername")
    val userUsername: String = "",
    @PropertyName("userPhoto")
    val userPhoto: String = "",
    @PropertyName("text")
    val text: String = "",
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Date? = null
)
