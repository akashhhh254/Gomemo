package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.firebase.FirebaseManager
import com.example.data.local.LocalSessionManager
import com.example.data.model.ActivityType
import com.example.data.model.AppNotification
import com.example.data.model.Comment
import com.example.data.model.Memory
import com.example.data.model.MemoryCollection
import com.example.data.model.NotificationType
import com.example.data.model.TimelineActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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

class MemoryRepository(
    private val firestore: FirebaseFirestore = FirebaseManager.firestore,
    private val storage: FirebaseStorage = FirebaseManager.storage
) {

    suspend fun uploadMemoryPhoto(
        userId: String,
        imageInputStream: InputStream
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val originalBitmap = BitmapFactory.decodeStream(imageInputStream)
                ?: throw Exception("Could not decode image file")

            val maxDimension = 1440
            val scale = if (originalBitmap.width > maxDimension || originalBitmap.height > maxDimension) {
                val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
                if (ratio > 1) {
                    val width = maxDimension
                    val height = (maxDimension / ratio).toInt()
                    Bitmap.createScaledBitmap(originalBitmap, width, height, true)
                } else {
                    val height = maxDimension
                    val width = (maxDimension * ratio).toInt()
                    Bitmap.createScaledBitmap(originalBitmap, width, height, true)
                }
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            scale.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val data = baos.toByteArray()

            val photoId = UUID.randomUUID().toString()
            val storageRef = storage.reference.child("memories/$userId/$photoId.jpg")
            storageRef.putBytes(data).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Photo upload failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createMemory(
        memory: Memory,
        authorName: String,
        authorPhoto: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (memory.photoUrls.isEmpty()) {
                throw Exception("A photo is strictly required to create a memory.")
            }
            val memoryId = if (memory.id.isNotBlank()) memory.id else UUID.randomUUID().toString()
            val memoryToSave = memory.copy(
                id = memoryId,
                authorName = authorName,
                authorPhoto = authorPhoto
            )

            // Save in Firestore
            firestore.collection("memories").document(memoryId).set(memoryToSave).await()
            firestore.collection("users").document(memory.userId)
                .update(
                    "memoriesCount", FieldValue.increment(1),
                    "placesVisitedCount", FieldValue.increment(1)
                ).await()

            // Create personal timeline entry
            val timelineActivity = TimelineActivity(
                id = UUID.randomUUID().toString(),
                userId = memory.userId,
                userName = authorName,
                userPhoto = authorPhoto,
                type = ActivityType.ADDED_MEMORY.name,
                title = "Added a new memory",
                description = "Captured memory at ${memory.placeName.ifBlank { "a place" }}: \"${memory.caption.take(80)}\"",
                placeName = memory.placeName,
                memoryId = memoryId,
                memoryPhoto = memory.photoUrls.firstOrNull() ?: ""
            )
            firestore.collection("timeline").add(timelineActivity).await()

            LocalSessionManager.addMemory(memoryToSave)
            LocalSessionManager.addActivity(timelineActivity)

            Result.success(memoryId)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Create memory failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Home Following Feed:
     * Shows real public memories from users the current user follows (plus current user's public memories).
     */
    fun getFollowingFeed(currentUserId: String): Flow<List<Memory>> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val followingRef = firestore.collection("users").document(currentUserId)
                .collection("following")
            
            val followingListener = followingRef.addSnapshotListener { snapshot, err ->
                if (err != null) {
                    Log.w("MemoryRepository", "Following fetch error: ${err.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val followedUserIds = mutableListOf<String>()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        doc.getString("userId")?.let { followedUserIds.add(it) }
                            ?: followedUserIds.add(doc.id)
                    }
                }
                followedUserIds.add(currentUserId)
                val distinctIds = followedUserIds.distinct()

                firestore.collection("memories")
                    .whereEqualTo("visibility", "public")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(60)
                    .get()
                    .addOnSuccessListener { memSnap ->
                        val allPublic = memSnap.toObjects(Memory::class.java)
                        val followingFeed = allPublic.filter { distinctIds.contains(it.userId) }
                        trySend(followingFeed)
                    }
                    .addOnFailureListener {
                        trySend(emptyList())
                    }
            }

            awaitClose { followingListener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    /**
     * Discover Feed:
     * Shows all real public memories uploaded by users across GoMemo.
     */
    fun getDiscoverFeed(): Flow<List<Memory>> = callbackFlow {
        try {
            val query = firestore.collection("memories")
                .whereEqualTo("visibility", "public")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MemoryRepository", "Discover feed error: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(Memory::class.java))
                } else {
                    trySend(emptyList())
                }
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    fun getPublicMemoriesFeed(): Flow<List<Memory>> = getDiscoverFeed()

    fun getUserMemories(userId: String): Flow<List<Memory>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val query = firestore.collection("memories")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(Memory::class.java))
                } else {
                    trySend(emptyList())
                }
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    fun getPublicUserMemories(userId: String): Flow<List<Memory>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val query = firestore.collection("memories")
                .whereEqualTo("userId", userId)
                .whereEqualTo("visibility", "public")
                .orderBy("createdAt", Query.Direction.DESCENDING)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(Memory::class.java))
                } else {
                    trySend(emptyList())
                }
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    fun getMemoryById(memoryId: String): Flow<Memory?> = callbackFlow {
        if (memoryId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        try {
            val docRef = firestore.collection("memories").document(memoryId)
            val listener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot.toObject(Memory::class.java))
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(null)
            awaitClose { }
        }
    }

    fun getMemoriesByIds(ids: List<String>): Flow<List<Memory>> = callbackFlow {
        if (ids.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            firestore.collection("memories")
                .whereIn("__name__", ids.take(30))
                .get()
                .addOnSuccessListener { snap ->
                    trySend(snap.toObjects(Memory::class.java))
                }
                .addOnFailureListener {
                    trySend(emptyList())
                }
            awaitClose { }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    fun searchMemoriesByTag(tag: String): Flow<List<Memory>> = callbackFlow {
        if (tag.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val cleanTag = tag.trim().lowercase().removePrefix("#")

        try {
            val query = firestore.collection("memories")
                .whereArrayContains("tags", cleanTag)
                .whereEqualTo("visibility", "public")
                .limit(50)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot.toObjects(Memory::class.java))
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    fun isMemoryLikedByUser(memoryId: String, userId: String): Flow<Boolean> = callbackFlow {
        if (memoryId.isBlank() || userId.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }

        try {
            val docRef = firestore.collection("memories").document(memoryId)
                .collection("likes").document(userId)
            val listener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                trySend(snapshot.exists())
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(false)
            awaitClose { }
        }
    }

    suspend fun toggleLike(
        memory: Memory,
        userId: String,
        userName: String,
        userPhoto: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val likeDoc = firestore.collection("memories").document(memory.id)
                .collection("likes").document(userId)
            val memoryDoc = firestore.collection("memories").document(memory.id)

            val existing = likeDoc.get().await()
            val nowLiked = !existing.exists()

            if (!nowLiked) {
                likeDoc.delete().await()
                memoryDoc.update("likesCount", FieldValue.increment(-1)).await()
            } else {
                val likeData = hashMapOf("userId" to userId, "createdAt" to FieldValue.serverTimestamp())
                likeDoc.set(likeData).await()
                memoryDoc.update("likesCount", FieldValue.increment(1)).await()

                if (memory.userId != userId) {
                    val notif = AppNotification(
                        recipientId = memory.userId,
                        senderId = userId,
                        senderName = userName,
                        senderPhoto = userPhoto,
                        type = NotificationType.LIKE.name,
                        memoryId = memory.id,
                        memoryPhoto = memory.photoUrls.firstOrNull() ?: "",
                        text = "liked your memory at ${memory.placeName.ifBlank { "a place" }}"
                    )
                    firestore.collection("notifications").add(notif).await()
                }
            }
            LocalSessionManager.toggleLike(memory.id)
            Result.success(nowLiked)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Toggle like error: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun isMemorySavedByUser(memoryId: String, userId: String): Flow<Boolean> = callbackFlow {
        if (memoryId.isBlank() || userId.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }

        try {
            val docRef = firestore.collection("users").document(userId)
                .collection("savedMemories").document(memoryId)
            val listener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                trySend(snapshot.exists())
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(false)
            awaitClose { }
        }
    }

    suspend fun saveMemory(memoryId: String, userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection("users").document(userId)
                .collection("savedMemories").document(memoryId)
            docRef.set(mapOf("memoryId" to memoryId, "savedAt" to FieldValue.serverTimestamp())).await()
            firestore.collection("users").document(userId)
                .update("savedMemories", FieldValue.arrayUnion(memoryId)).await()
            LocalSessionManager.toggleSave(memoryId)
            Result.success(true)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Save error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun unsaveMemory(memoryId: String, userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection("users").document(userId)
                .collection("savedMemories").document(memoryId)
            docRef.delete().await()
            firestore.collection("users").document(userId)
                .update("savedMemories", FieldValue.arrayRemove(memoryId)).await()
            LocalSessionManager.toggleSave(memoryId)
            Result.success(false)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Unsave error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun toggleSaveMemory(memoryId: String, userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection("users").document(userId)
                .collection("savedMemories").document(memoryId)
            val check = docRef.get().await()
            val isNowSaved = !check.exists()

            if (isNowSaved) {
                docRef.set(mapOf("memoryId" to memoryId, "savedAt" to FieldValue.serverTimestamp())).await()
                firestore.collection("users").document(userId)
                    .update("savedMemories", FieldValue.arrayUnion(memoryId)).await()
            } else {
                docRef.delete().await()
                firestore.collection("users").document(userId)
                    .update("savedMemories", FieldValue.arrayRemove(memoryId)).await()
            }
            LocalSessionManager.toggleSave(memoryId)
            Result.success(isNowSaved)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Toggle save error: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getSavedMemories(userId: String): Flow<List<Memory>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val savedRef = firestore.collection("users").document(userId)
                .collection("savedMemories")
                .orderBy("savedAt", Query.Direction.DESCENDING)

            val listener = savedRef.addSnapshotListener { snapshot, err ->
                if (err != null || snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val savedIds = snapshot.documents.map { it.id }
                if (savedIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                firestore.collection("memories")
                    .whereIn("__name__", savedIds.take(10))
                    .get()
                    .addOnSuccessListener { memSnap ->
                        trySend(memSnap.toObjects(Memory::class.java))
                    }
                    .addOnFailureListener {
                        trySend(emptyList())
                    }
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    fun getComments(memoryId: String): Flow<List<Comment>> = callbackFlow {
        if (memoryId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val query = firestore.collection("memories").document(memoryId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot.toObjects(Comment::class.java))
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    suspend fun addComment(
        memory: Memory,
        userId: String,
        userName: String,
        userUsername: String,
        userPhoto: String,
        text: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val commentId = UUID.randomUUID().toString()
            val comment = Comment(
                id = commentId,
                memoryId = memory.id,
                userId = userId,
                userName = userName,
                userUsername = userUsername,
                userPhoto = userPhoto,
                text = text.trim()
            )

            val commentDoc = firestore.collection("memories").document(memory.id)
                .collection("comments").document(commentId)
            commentDoc.set(comment).await()
            firestore.collection("memories").document(memory.id)
                .update("commentsCount", FieldValue.increment(1)).await()

            if (memory.userId != userId) {
                val notif = AppNotification(
                    recipientId = memory.userId,
                    senderId = userId,
                    senderName = userName,
                    senderUsername = userUsername,
                    senderPhoto = userPhoto,
                    type = NotificationType.COMMENT.name,
                    memoryId = memory.id,
                    memoryPhoto = memory.photoUrls.firstOrNull() ?: "",
                    text = "commented: \"${text.take(50)}\""
                )
                firestore.collection("notifications").add(notif).await()
            }

            LocalSessionManager.addComment(comment)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Add comment error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(memoryId: String, commentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("memories").document(memoryId)
                .collection("comments").document(commentId).delete().await()
            firestore.collection("memories").document(memoryId)
                .update("commentsCount", FieldValue.increment(-1)).await()

            LocalSessionManager.deleteComment(memoryId, commentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Delete comment error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("memories").document(memoryId).delete().await()
            firestore.collection("users").document(userId)
                .update("memoriesCount", FieldValue.increment(-1)).await()

            LocalSessionManager.deleteMemory(memoryId, userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Delete memory error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun searchMemoriesByPlace(query: String): List<Memory> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val clean = query.trim()
        try {
            val snapshot = firestore.collection("memories")
                .whereEqualTo("visibility", "public")
                .limit(50)
                .get()
                .await()
            val all = snapshot.toObjects(Memory::class.java)
            all.filter {
                it.placeName.contains(clean, ignoreCase = true) ||
                it.locationName.contains(clean, ignoreCase = true) ||
                it.caption.contains(clean, ignoreCase = true) ||
                it.city.contains(clean, ignoreCase = true) ||
                it.country.contains(clean, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Search memories error: ${e.message}", e)
            emptyList()
        }
    }

    // Collections
    fun getUserCollections(userId: String): Flow<List<MemoryCollection>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val query = firestore.collection("users").document(userId)
                .collection("collections")
                .orderBy("createdAt", Query.Direction.DESCENDING)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot.toObjects(MemoryCollection::class.java))
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    suspend fun createCollection(
        userId: String,
        name: String,
        description: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val collectionId = UUID.randomUUID().toString()
            val col = MemoryCollection(
                id = collectionId,
                userId = userId,
                name = name.trim(),
                description = description.trim()
            )
            firestore.collection("users").document(userId)
                .collection("collections").document(collectionId).set(col).await()

            LocalSessionManager.addCollection(col)
            Result.success(collectionId)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Create collection error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun addMemoryToCollection(
        userId: String,
        collectionId: String,
        memoryId: String,
        memoryPhotoUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection("users").document(userId)
                .collection("collections").document(collectionId)
            docRef.update(
                "memoryIds", FieldValue.arrayUnion(memoryId),
                "coverPhoto", memoryPhotoUrl
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Add to collection error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUserLocationHistory(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val memories = firestore.collection("memories").whereEqualTo("userId", userId).get().await()
            for (doc in memories.documents) {
                doc.reference.update(mapOf("latitude" to 0.0, "longitude" to 0.0, "locationName" to "Private Location")).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MemoryRepository", "Delete location history error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
