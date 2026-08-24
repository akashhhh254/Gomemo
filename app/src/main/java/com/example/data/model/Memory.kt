package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Memory(
    @DocumentId
    val id: String = "",
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("authorName")
    val authorName: String = "",
    @PropertyName("authorUsername")
    val authorUsername: String = "",
    @PropertyName("authorPhoto")
    val authorPhoto: String = "",
    @PropertyName("placeName")
    val placeName: String = "",
    @PropertyName("locationName")
    val locationName: String = "",
    @PropertyName("city")
    val city: String = "",
    @PropertyName("country")
    val country: String = "",
    @PropertyName("latitude")
    val latitude: Double = 0.0,
    @PropertyName("longitude")
    val longitude: Double = 0.0,
    @PropertyName("dateVisited")
    val dateVisited: String = "",
    @PropertyName("timeVisited")
    val timeVisited: String = "",
    @PropertyName("photoUrls")
    val photoUrls: List<String> = emptyList(),
    @PropertyName("caption")
    val caption: String = "",
    @PropertyName("tags")
    val tags: List<String> = emptyList(),
    @PropertyName("visibility")
    val visibility: String = "public", // "public" or "private"
    @PropertyName("likesCount")
    val likesCount: Long = 0,
    @PropertyName("commentsCount")
    val commentsCount: Long = 0,
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Date? = null
)
