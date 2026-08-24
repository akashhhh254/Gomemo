package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.firebase.FirebaseManager
import com.example.data.model.Story
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
import java.util.Date
import java.util.UUID

class StoryRepository(
    private val firestore: FirebaseFirestore = FirebaseManager.firestore,
    private val storage: FirebaseStorage = FirebaseManager.storage
) {

    suspend fun uploadStoryPhoto(
        userId: String,
        inputStream: InputStream
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeStream(inputStream)
                ?: throw Exception("Could not decode story image")

            val maxDim = 1280
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                if (ratio > 1) {
                    val w = maxDim
                    val h = (maxDim / ratio).toInt()
                    Bitmap.createScaledBitmap(bitmap, w, h, true)
                } else {
                    val h = maxDim
                    val w = (maxDim * ratio).toInt()
                    Bitmap.createScaledBitmap(bitmap, w, h, true)
                }
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val data = baos.toByteArray()

            val storyPhotoId = UUID.randomUUID().toString()
            val ref = storage.reference.child("stories/$userId/$storyPhotoId.jpg")
            ref.putBytes(data).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: Exception) {
            Log.e("StoryRepository", "Upload story photo error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun publishStory(
        userProfile: UserProfile,
        photoUrl: String,
        placeName: String = "",
        caption: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val storyId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val expiresAt = Date(now + 24 * 60 * 60 * 1000) // 24 hours validity

            val story = Story(
                id = storyId,
                userId = userProfile.uid,
                userName = userProfile.fullName,
                userUsername = userProfile.username,
                userPhoto = userProfile.profilePhoto,
                photoUrl = photoUrl,
                placeName = placeName.trim(),
                caption = caption.trim(),
                viewers = emptyList(),
                expiresAt = expiresAt
            )

            firestore.collection("stories").document(storyId).set(story).await()
            Result.success(storyId)
        } catch (e: Exception) {
            Log.e("StoryRepository", "Publish story error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Active unexpired stories from following users and current user
     */
    fun getActiveStories(currentUserId: String): Flow<List<Story>> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val userRef = firestore.collection("users").document(currentUserId)
            val userSub = userRef.addSnapshotListener { userSnap, err ->
                if (err != null || userSnap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val followingList = userSnap.get("following") as? List<*> ?: emptyList<Any>()
                val allowedUserIds = (followingList.mapNotNull { it?.toString() } + currentUserId).distinct()

                val now = Date()
                firestore.collection("stories")
                    .whereGreaterThan("expiresAt", now)
                    .orderBy("expiresAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .addOnSuccessListener { querySnap ->
                        val validStories = querySnap.toObjects(Story::class.java)
                            .filter { allowedUserIds.contains(it.userId) }
                        trySend(validStories)
                    }
                    .addOnFailureListener {
                        trySend(emptyList())
                    }
            }

            awaitClose { userSub.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    suspend fun markStoryViewed(storyId: String, currentUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (storyId.isBlank() || currentUserId.isBlank()) return@withContext Result.success(Unit)
            firestore.collection("stories").document(storyId)
                .update("viewers", FieldValue.arrayUnion(currentUserId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("stories").document(storyId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
