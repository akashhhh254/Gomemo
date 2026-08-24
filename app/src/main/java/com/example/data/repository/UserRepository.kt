package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.firebase.FirebaseManager
import com.example.data.local.LocalSessionManager
import com.example.data.model.ActivityType
import com.example.data.model.AppNotification
import com.example.data.model.NotificationType
import com.example.data.model.TimelineActivity
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseManager.firestore,
    private val storage: FirebaseStorage = FirebaseManager.storage
) {

    fun getUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        try {
            val docRef = firestore.collection("users").document(userId)
            val listener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("UserRepository", "User profile fetch error: ${error.message}")
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toObject(UserProfile::class.java)
                    trySend(profile)
                } else {
                    trySend(null)
                }
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            Log.e("UserRepository", "getUserProfile error: ${e.message}", e)
            trySend(null)
            awaitClose { }
        }
    }

    suspend fun updateProfile(
        userId: String,
        fullName: String,
        username: String,
        bio: String,
        profilePhotoUrl: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mutableMapOf<String, Any>(
                "fullName" to fullName.trim(),
                "username" to username.trim().lowercase(),
                "bio" to bio.trim()
            )
            if (profilePhotoUrl != null) {
                updates["profilePhoto"] = profilePhotoUrl
            }
            firestore.collection("users").document(userId).update(updates).await()
            LocalSessionManager.updateUserProfile(userId, fullName, username, bio, profilePhotoUrl)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Update profile error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        userId: String,
        locationTrackingEnabled: Boolean,
        isPrivateAccount: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(userId).update(
                mapOf(
                    "locationTrackingEnabled" to locationTrackingEnabled,
                    "isPrivateAccount" to isPrivateAccount
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Update privacy error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadProfilePhoto(
        userId: String,
        imageInputStream: InputStream
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeStream(imageInputStream)
                ?: throw Exception("Failed to decode image stream")
            val dimension = 512
            val scaled = Bitmap.createScaledBitmap(bitmap, dimension, dimension, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            val data = baos.toByteArray()

            val ref = storage.reference.child("users/$userId/profile_${System.currentTimeMillis()}.jpg")
            ref.putBytes(data).await()
            val url = ref.downloadUrl.await().toString()

            firestore.collection("users").document(userId).update("profilePhoto", url).await()
            Result.success(url)
        } catch (e: Exception) {
            Log.e("UserRepository", "Profile photo upload error: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun isFollowing(currentUserId: String, targetUserId: String): Flow<Boolean> = callbackFlow {
        if (currentUserId.isBlank() || targetUserId.isBlank() || currentUserId == targetUserId) {
            trySend(false)
            close()
            return@callbackFlow
        }

        try {
            val docRef = firestore.collection("users").document(currentUserId)
                .collection("following").document(targetUserId)

            val listener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                trySend(snapshot != null && snapshot.exists())
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(false)
            awaitClose { }
        }
    }

    suspend fun followUser(
        currentUser: UserProfile,
        targetUser: UserProfile
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = currentUser.uid
            val targetUserId = targetUser.uid
            if (currentUserId == targetUserId) return@withContext Result.success(Unit)

            val followingDoc = firestore.collection("users").document(currentUserId)
                .collection("following").document(targetUserId)
            val followersDoc = firestore.collection("users").document(targetUserId)
                .collection("followers").document(currentUserId)

            val check = followingDoc.get().await()
            if (check.exists()) return@withContext Result.success(Unit)

            firestore.runBatch { batch ->
                batch.set(followingDoc, mapOf("userId" to targetUserId, "timestamp" to FieldValue.serverTimestamp()))
                batch.set(followersDoc, mapOf("userId" to currentUserId, "timestamp" to FieldValue.serverTimestamp()))
                batch.update(firestore.collection("users").document(currentUserId), "followingCount", FieldValue.increment(1))
                batch.update(firestore.collection("users").document(currentUserId), "following", FieldValue.arrayUnion(targetUserId))
                batch.update(firestore.collection("users").document(targetUserId), "followersCount", FieldValue.increment(1))
            }.await()

            val notif = AppNotification(
                recipientId = targetUserId,
                senderId = currentUserId,
                senderName = currentUser.fullName,
                senderUsername = currentUser.username,
                senderPhoto = currentUser.profilePhoto,
                type = NotificationType.NEW_FOLLOWER.name,
                text = "started following you"
            )
            firestore.collection("notifications").add(notif).await()

            val activity = TimelineActivity(
                userId = currentUserId,
                userName = currentUser.fullName,
                userPhoto = currentUser.profilePhoto,
                type = ActivityType.FOLLOWED_USER.name,
                title = "Started following",
                description = "Started following ${targetUser.fullName} (@${targetUser.username})",
                targetUserId = targetUserId,
                targetUserName = targetUser.fullName
            )
            firestore.collection("timeline").add(activity).await()
            LocalSessionManager.toggleFollow(targetUserId)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Follow error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun followUser(
        currentUserId: String,
        targetUserId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (currentUserId == targetUserId) return@withContext Result.success(Unit)

            val currentUserSnap = firestore.collection("users").document(currentUserId).get().await()
            val targetUserSnap = firestore.collection("users").document(targetUserId).get().await()

            val currentUser = currentUserSnap.toObject(UserProfile::class.java)
            val targetUser = targetUserSnap.toObject(UserProfile::class.java)

            if (currentUser != null && targetUser != null) {
                return@withContext followUser(currentUser, targetUser)
            }

            val followingDoc = firestore.collection("users").document(currentUserId)
                .collection("following").document(targetUserId)
            val followersDoc = firestore.collection("users").document(targetUserId)
                .collection("followers").document(currentUserId)

            firestore.runBatch { batch ->
                batch.set(followingDoc, mapOf("userId" to targetUserId, "timestamp" to FieldValue.serverTimestamp()))
                batch.set(followersDoc, mapOf("userId" to currentUserId, "timestamp" to FieldValue.serverTimestamp()))
                batch.update(firestore.collection("users").document(currentUserId), "followingCount", FieldValue.increment(1))
                batch.update(firestore.collection("users").document(currentUserId), "following", FieldValue.arrayUnion(targetUserId))
                batch.update(firestore.collection("users").document(targetUserId), "followersCount", FieldValue.increment(1))
            }.await()

            LocalSessionManager.toggleFollow(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Follow error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun unfollowUser(
        currentUserId: String,
        targetUserId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (currentUserId == targetUserId) return@withContext Result.success(Unit)

            val followingDoc = firestore.collection("users").document(currentUserId)
                .collection("following").document(targetUserId)
            val followersDoc = firestore.collection("users").document(targetUserId)
                .collection("followers").document(currentUserId)

            firestore.runBatch { batch ->
                batch.delete(followingDoc)
                batch.delete(followersDoc)
                batch.update(firestore.collection("users").document(currentUserId), "followingCount", FieldValue.increment(-1))
                batch.update(firestore.collection("users").document(currentUserId), "following", FieldValue.arrayRemove(targetUserId))
                batch.update(firestore.collection("users").document(targetUserId), "followersCount", FieldValue.increment(-1))
            }.await()

            LocalSessionManager.toggleFollow(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Unfollow error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String): List<UserProfile> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val clean = query.trim().lowercase().removePrefix("@")
        try {
            val snapshot = firestore.collection("users")
                .limit(40)
                .get()
                .await()

            val users = snapshot.toObjects(UserProfile::class.java)
            users.filter {
                it.username.contains(clean, ignoreCase = true) ||
                it.fullName.contains(clean, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Search users error: ${e.message}", e)
            emptyList()
        }
    }

    fun getTimelineActivities(userId: String): Flow<List<TimelineActivity>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val query = firestore.collection("timeline")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot.toObjects(TimelineActivity::class.java))
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    fun getNotifications(userId: String): Flow<List<AppNotification>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val query = firestore.collection("notifications")
                .whereEqualTo("recipientId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot.toObjects(AppNotification::class.java))
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("notifications").document(notificationId)
                .update("read", true).await()
            LocalSessionManager.markNotificationRead(notificationId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Mark notification error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun isUsernameAvailable(username: String, currentUserId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val clean = username.trim().lowercase().removePrefix("@")
        if (clean.length < 3 || clean.length > 30) return@withContext false
        try {
            val query = firestore.collection("users")
                .whereEqualTo("username", clean)
                .limit(2)
                .get()
                .await()

            if (query.isEmpty) return@withContext true
            val docs = query.documents
            if (docs.size == 1 && currentUserId != null && docs[0].id == currentUserId) {
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e("UserRepository", "isUsernameAvailable error: ${e.message}", e)
            true
        }
    }

    fun getRelationshipStatus(currentUserId: String, targetUserId: String): Flow<com.example.data.model.FollowStatus> = callbackFlow {
        if (currentUserId.isBlank() || targetUserId.isBlank() || currentUserId == targetUserId) {
            trySend(com.example.data.model.FollowStatus.NONE)
            close()
            return@callbackFlow
        }

        try {
            val followingDoc = firestore.collection("users").document(currentUserId)
                .collection("following").document(targetUserId)
            val requestDoc = firestore.collection("users").document(targetUserId)
                .collection("followRequests").document(currentUserId)

            var isFollowing = false
            var isRequested = false

            val folSub = followingDoc.addSnapshotListener { fSnap, _ ->
                isFollowing = fSnap != null && fSnap.exists()
                if (isFollowing) {
                    trySend(com.example.data.model.FollowStatus.FOLLOWING)
                } else if (isRequested) {
                    trySend(com.example.data.model.FollowStatus.REQUESTED)
                } else {
                    trySend(com.example.data.model.FollowStatus.NONE)
                }
            }

            val reqSub = requestDoc.addSnapshotListener { rSnap, _ ->
                isRequested = rSnap != null && rSnap.exists()
                if (isFollowing) {
                    trySend(com.example.data.model.FollowStatus.FOLLOWING)
                } else if (isRequested) {
                    trySend(com.example.data.model.FollowStatus.REQUESTED)
                } else {
                    trySend(com.example.data.model.FollowStatus.NONE)
                }
            }

            awaitClose {
                folSub.remove()
                reqSub.remove()
            }
        } catch (e: Exception) {
            trySend(com.example.data.model.FollowStatus.NONE)
            awaitClose { }
        }
    }

    suspend fun requestFollow(currentUser: UserProfile, targetUser: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = com.example.data.model.FollowRequest(
                id = currentUser.uid,
                fromUserId = currentUser.uid,
                fromUserName = currentUser.fullName,
                fromUserUsername = currentUser.username,
                fromUserPhoto = currentUser.profilePhoto,
                toUserId = targetUser.uid,
                status = "pending"
            )

            firestore.collection("users").document(targetUser.uid)
                .collection("followRequests").document(currentUser.uid)
                .set(request).await()

            val notif = AppNotification(
                recipientId = targetUser.uid,
                senderId = currentUser.uid,
                senderName = currentUser.fullName,
                senderUsername = currentUser.username,
                senderPhoto = currentUser.profilePhoto,
                type = "FOLLOW_REQUEST",
                text = "requested to follow you"
            )
            firestore.collection("notifications").add(notif).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Request follow error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun cancelFollowRequest(currentUserId: String, targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(targetUserId)
                .collection("followRequests").document(currentUserId)
                .delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPendingFollowRequests(userId: String): Flow<List<com.example.data.model.FollowRequest>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val query = firestore.collection("users").document(userId)
                .collection("followRequests")

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot.toObjects(com.example.data.model.FollowRequest::class.java))
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    suspend fun acceptFollowRequest(requester: UserProfile, targetUser: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(targetUser.uid)
                .collection("followRequests").document(requester.uid)
                .delete().await()

            followUser(requester, targetUser)

            val notif = AppNotification(
                recipientId = requester.uid,
                senderId = targetUser.uid,
                senderName = targetUser.fullName,
                senderUsername = targetUser.username,
                senderPhoto = targetUser.profilePhoto,
                type = "FOLLOW_REQUEST_ACCEPTED",
                text = "accepted your follow request"
            )
            firestore.collection("notifications").add(notif).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptFollowRequest(currentUserId: String, requester: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(currentUserId)
                .collection("followRequests").document(requester.uid)
                .delete().await()

            val currentUserSnap = firestore.collection("users").document(currentUserId).get().await()
            val currentUser = currentUserSnap.toObject(UserProfile::class.java)

            if (currentUser != null) {
                followUser(requester, currentUser)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun declineFollowRequest(requesterId: String, currentUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(currentUserId)
                .collection("followRequests").document(requesterId)
                .delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun blockUser(currentUserId: String, targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            unfollowUser(currentUserId, targetUserId)
            unfollowUser(targetUserId, currentUserId)
            firestore.collection("users").document(currentUserId)
                .collection("blocked").document(targetUserId)
                .set(mapOf("blockedAt" to FieldValue.serverTimestamp())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportUser(
        currentUserId: String,
        targetUserId: String,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("reports").add(
                mapOf(
                    "reporterId" to currentUserId,
                    "reportedUserId" to targetUserId,
                    "reason" to reason,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
