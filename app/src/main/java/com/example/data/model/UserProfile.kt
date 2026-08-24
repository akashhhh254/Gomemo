package com.example.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class UserProfile(
    @DocumentId
    val uid: String = "",
    @PropertyName("fullName")
    val fullName: String = "",
    @PropertyName("email")
    val email: String = "",
    @PropertyName("username")
    val username: String = "",
    @PropertyName("profilePhoto")
    val profilePhoto: String = "",
    @PropertyName("bio")
    val bio: String = "",
    @PropertyName("publicKey")
    val publicKey: String = "", // Base64 public key for End-to-End Encryption
    @PropertyName("followersCount")
    val followersCount: Long = 0,
    @PropertyName("followingCount")
    val followingCount: Long = 0,
    @PropertyName("memoriesCount")
    val memoriesCount: Long = 0,
    @PropertyName("placesVisitedCount")
    val placesVisitedCount: Long = 0,
    @PropertyName("following")
    val following: List<String> = emptyList(),
    @PropertyName("savedMemories")
    val savedMemories: List<String> = emptyList(),
    @PropertyName("locationTrackingEnabled")
    val locationTrackingEnabled: Boolean = true,
    @PropertyName("isPrivateAccount")
    val isPrivateAccount: Boolean = false,
    @ServerTimestamp
    @PropertyName("createdAt")
    val createdAt: Date? = null
)
