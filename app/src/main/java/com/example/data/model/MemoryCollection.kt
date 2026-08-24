package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class MemoryCollection(
    @DocumentId
    val id: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("name")
    val name: String = "",
    @PropertyName("description")
    val description: String = "",
    @PropertyName("coverPhoto")
    val coverPhoto: String = "",
    @PropertyName("memoryIds")
    val memoryIds: List<String> = emptyList(),
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Date? = null
)
