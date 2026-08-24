package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.crypto.CryptoManager
import com.example.data.firebase.FirebaseManager
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
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

class ChatRepository(
    private val firestore: FirebaseFirestore = FirebaseManager.firestore,
    private val storage: FirebaseStorage = FirebaseManager.storage
) {

    /**
     * Listen to all active chats for the given user.
     */
    fun getUserChats(userId: String): Flow<List<Chat>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val query = firestore.collection("chats")
                .whereArrayContains("participants", userId)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w("ChatRepository", "User chats error: ${error?.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot.toObjects(Chat::class.java))
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            Log.e("ChatRepository", "getUserChats exception: ${e.message}", e)
            trySend(emptyList())
            awaitClose { }
        }
    }

    /**
     * Get or create a 1-on-1 chat between two users.
     */
    suspend fun getOrCreateChat(currentUserId: String, targetUserId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (currentUserId == targetUserId) {
                throw Exception("Cannot chat with yourself")
            }

            // Search for existing chat
            val existing = firestore.collection("chats")
                .whereArrayContains("participants", currentUserId)
                .get()
                .await()

            for (doc in existing.documents) {
                val participants = doc.get("participants") as? List<*> ?: continue
                if (participants.contains(targetUserId)) {
                    return@withContext Result.success(doc.id)
                }
            }

            // Create new chat
            val chatId = UUID.randomUUID().toString()
            val newChat = hashMapOf(
                "id" to chatId,
                "participants" to listOf(currentUserId, targetUserId),
                "lastMessageCiphertext" to "",
                "lastMessageNonce" to "",
                "lastMessageSenderId" to "",
                "isPhotoMessage" to false,
                "unreadCounts" to mapOf(currentUserId to 0L, targetUserId to 0L),
                "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("chats").document(chatId).set(newChat).await()
            Result.success(chatId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Get or create chat error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Listen to real-time messages in a chat.
     */
    fun getChatMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        if (chatId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        try {
            val query = firestore.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(100)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot.toObjects(ChatMessage::class.java))
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            trySend(emptyList())
            awaitClose { }
        }
    }

    /**
     * Send end-to-end encrypted text message.
     * Ciphertext is computed locally on device; server receives only encrypted ciphertext and nonce.
     */
    suspend fun sendTextMessage(
        chatId: String,
        senderId: String,
        recipientId: String,
        recipientPublicKey: String,
        plaintext: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (recipientPublicKey.isBlank()) {
                throw Exception("Recipient public key not found for E2EE.")
            }

            val (ciphertext, nonce) = CryptoManager.encryptMessage(plaintext.trim(), recipientPublicKey)
            val messageId = UUID.randomUUID().toString()

            val message = ChatMessage(
                id = messageId,
                chatId = chatId,
                senderId = senderId,
                recipientId = recipientId,
                ciphertext = ciphertext,
                nonce = nonce,
                isPhoto = false,
                status = "sent"
            )

            val chatRef = firestore.collection("chats").document(chatId)
            val messageRef = chatRef.collection("messages").document(messageId)

            firestore.runBatch { batch ->
                batch.set(messageRef, message)
                batch.set(messageRef, mapOf("timestamp" to FieldValue.serverTimestamp()), SetOptions.merge())
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessageCiphertext" to ciphertext,
                        "lastMessageNonce" to nonce,
                        "lastMessageSenderId" to senderId,
                        "isPhotoMessage" to false,
                        "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                        "unreadCounts.$recipientId" to FieldValue.increment(1)
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Send text message error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Send end-to-end encrypted private photo.
     * Raw photo bytes are encrypted on device with AES-GCM; server receives only encrypted opaque blob.
     */
    suspend fun sendPrivatePhoto(
        chatId: String,
        senderId: String,
        recipientId: String,
        recipientPublicKey: String,
        imageInputStream: InputStream
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (recipientPublicKey.isBlank()) {
                throw Exception("Recipient public key not found for E2EE photo sharing.")
            }

            val original = BitmapFactory.decodeStream(imageInputStream)
                ?: throw Exception("Could not decode image stream")

            val maxDimension = 1080
            val scale = if (original.width > maxDimension || original.height > maxDimension) {
                val ratio = original.width.toFloat() / original.height.toFloat()
                if (ratio > 1) {
                    Bitmap.createScaledBitmap(original, maxDimension, (maxDimension / ratio).toInt(), true)
                } else {
                    Bitmap.createScaledBitmap(original, (maxDimension * ratio).toInt(), maxDimension, true)
                }
            } else {
                original
            }

            val baos = ByteArrayOutputStream()
            scale.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val rawPhotoBytes = baos.toByteArray()

            // 1. Encrypt raw photo bytes locally
            val (encryptedBytes, encryptedKeyBase64, nonceBase64) = CryptoManager.encryptPhotoBytes(
                rawBytes = rawPhotoBytes,
                recipientPublicKeyBase64 = recipientPublicKey
            )

            // 2. Upload ciphertext blob to Firebase Storage
            val messageId = UUID.randomUUID().toString()
            val storageRef = storage.reference.child("private_chats/$chatId/$messageId.enc")
            storageRef.putBytes(encryptedBytes).await()
            val encryptedPhotoUrl = storageRef.downloadUrl.await().toString()

            // 3. Save message metadata in Firestore
            val message = ChatMessage(
                id = messageId,
                chatId = chatId,
                senderId = senderId,
                recipientId = recipientId,
                ciphertext = "[E2EE Photo]",
                nonce = nonceBase64,
                isPhoto = true,
                encryptedPhotoUrl = encryptedPhotoUrl,
                encryptedPhotoKey = encryptedKeyBase64,
                status = "sent"
            )

            val chatRef = firestore.collection("chats").document(chatId)
            val messageRef = chatRef.collection("messages").document(messageId)

            firestore.runBatch { batch ->
                batch.set(messageRef, message)
                batch.set(messageRef, mapOf("timestamp" to FieldValue.serverTimestamp()), SetOptions.merge())
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessageCiphertext" to "[E2EE Photo]",
                        "lastMessageNonce" to nonceBase64,
                        "lastMessageSenderId" to senderId,
                        "isPhotoMessage" to true,
                        "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                        "unreadCounts.$recipientId" to FieldValue.increment(1)
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Send private photo error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Download and decrypt an E2EE private photo locally.
     */
    suspend fun downloadAndDecryptPhoto(
        encryptedPhotoUrl: String,
        encryptedPhotoKey: String,
        nonce: String,
        senderPublicKey: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Download ciphertext bytes from Storage URL
            val storageRef = storage.getReferenceFromUrl(encryptedPhotoUrl)
            val maxDownloadSizeBytes: Long = 10 * 1024 * 1024 // 10MB
            val encryptedBytes = storageRef.getBytes(maxDownloadSizeBytes).await()

            // Decrypt on device
            val decryptedBytes = CryptoManager.decryptPhotoBytes(
                encryptedPhotoData = encryptedBytes,
                encryptedKeyBase64 = encryptedPhotoKey,
                nonceBase64 = nonce,
                senderPublicKeyBase64 = senderPublicKey
            ) ?: return@withContext null

            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to download and decrypt photo: ${e.message}", e)
            null
        }
    }

    suspend fun markChatAsRead(chatId: String, userId: String) = withContext(Dispatchers.IO) {
        try {
            firestore.collection("chats").document(chatId)
                .update("unreadCounts.$userId", 0L).await()
        } catch (e: Exception) {
            Log.w("ChatRepository", "Mark chat read error: ${e.message}")
        }
    }
}
