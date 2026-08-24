package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppNotification
import com.example.data.model.Comment
import com.example.data.model.Memory
import com.example.data.model.MemoryCollection
import com.example.data.model.TimelineActivity
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object LocalSessionManager {
    private var prefs: SharedPreferences? = null

    private val _currentUserFlow = MutableStateFlow<UserProfile?>(null)
    val currentUserFlow: StateFlow<UserProfile?> = _currentUserFlow.asStateFlow()

    private val _memoriesFlow = MutableStateFlow<List<Memory>>(emptyList())
    val memoriesFlow: StateFlow<List<Memory>> = _memoriesFlow.asStateFlow()

    private val _likedMemoryIds = MutableStateFlow<MutableSet<String>>(mutableSetOf())
    val likedMemoryIds: StateFlow<Set<String>> = _likedMemoryIds.asStateFlow()

    private val _savedMemoryIds = MutableStateFlow<MutableSet<String>>(mutableSetOf())
    val savedMemoryIds: StateFlow<Set<String>> = _savedMemoryIds.asStateFlow()

    private val _followingUserIds = MutableStateFlow<MutableSet<String>>(mutableSetOf())
    val followingUserIds: StateFlow<Set<String>> = _followingUserIds.asStateFlow()

    private val _commentsFlow = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    val commentsFlow: StateFlow<Map<String, List<Comment>>> = _commentsFlow.asStateFlow()

    private val _activitiesFlow = MutableStateFlow<List<TimelineActivity>>(emptyList())
    val activitiesFlow: StateFlow<List<TimelineActivity>> = _activitiesFlow.asStateFlow()

    private val _notificationsFlow = MutableStateFlow<List<AppNotification>>(emptyList())
    val notificationsFlow: StateFlow<List<AppNotification>> = _notificationsFlow.asStateFlow()

    private val _collectionsFlow = MutableStateFlow<List<MemoryCollection>>(emptyList())
    val collectionsFlow: StateFlow<List<MemoryCollection>> = _collectionsFlow.asStateFlow()

    private val _usersMap = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val usersMap: StateFlow<Map<String, UserProfile>> = _usersMap.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("gomemo_local_session", Context.MODE_PRIVATE)
            loadSavedSession()
        }
    }

    private fun loadSavedSession() {
        val savedUid = prefs?.getString("current_uid", null)
        val savedName = prefs?.getString("current_name", null)
        val savedEmail = prefs?.getString("current_email", null)
        val savedUsername = prefs?.getString("current_username", null)
        val savedPhoto = prefs?.getString("current_photo", "") ?: ""
        val savedBio = prefs?.getString("current_bio", "") ?: ""
        val savedPublicKey = prefs?.getString("current_public_key", "") ?: ""

        if (!savedUid.isNullOrBlank() && !savedEmail.isNullOrBlank()) {
            val restoredUser = UserProfile(
                uid = savedUid,
                fullName = savedName ?: "",
                email = savedEmail,
                username = savedUsername ?: "",
                profilePhoto = savedPhoto,
                bio = savedBio,
                publicKey = savedPublicKey,
                followersCount = 0,
                followingCount = 0,
                memoriesCount = 0
            )
            _currentUserFlow.value = restoredUser
            _usersMap.value = mapOf(restoredUser.uid to restoredUser)
        }
    }

    fun saveUserSession(profile: UserProfile) {
        _currentUserFlow.value = profile
        _usersMap.update { current ->
            current + (profile.uid to profile)
        }
        prefs?.edit()?.apply {
            putString("current_uid", profile.uid)
            putString("current_name", profile.fullName)
            putString("current_email", profile.email)
            putString("current_username", profile.username)
            putString("current_photo", profile.profilePhoto)
            putString("current_bio", profile.bio)
            putString("current_public_key", profile.publicKey)
            apply()
        }
    }

    fun clearUserSession() {
        _currentUserFlow.value = null
        _memoriesFlow.value = emptyList()
        _likedMemoryIds.value = mutableSetOf()
        _savedMemoryIds.value = mutableSetOf()
        _followingUserIds.value = mutableSetOf()
        _commentsFlow.value = emptyMap()
        _activitiesFlow.value = emptyList()
        _notificationsFlow.value = emptyList()
        _collectionsFlow.value = emptyList()
        _usersMap.value = emptyMap()
        prefs?.edit()?.clear()?.apply()
    }

    fun addMemory(memory: Memory) {
        _memoriesFlow.update { current ->
            listOf(memory) + current.filterNot { it.id == memory.id }
        }
        val current = _currentUserFlow.value
        if (current != null && current.uid == memory.userId) {
            val updated = current.copy(memoriesCount = current.memoriesCount + 1)
            saveUserSession(updated)
        }
    }

    fun deleteMemory(memoryId: String, userId: String) {
        _memoriesFlow.update { current ->
            current.filterNot { it.id == memoryId }
        }
        val current = _currentUserFlow.value
        if (current != null && current.uid == userId) {
            val updated = current.copy(memoriesCount = maxOf(0, current.memoriesCount - 1))
            saveUserSession(updated)
        }
    }

    fun toggleLike(memoryId: String): Boolean {
        val set = _likedMemoryIds.value.toMutableSet()
        val isLiked = if (set.contains(memoryId)) {
            set.remove(memoryId)
            false
        } else {
            set.add(memoryId)
            true
        }
        _likedMemoryIds.value = set

        _memoriesFlow.update { list ->
            list.map { mem ->
                if (mem.id == memoryId) {
                    val count = if (isLiked) mem.likesCount + 1 else maxOf(0, mem.likesCount - 1)
                    mem.copy(likesCount = count)
                } else mem
            }
        }
        return isLiked
    }

    fun toggleSave(memoryId: String): Boolean {
        val set = _savedMemoryIds.value.toMutableSet()
        val isSaved = if (set.contains(memoryId)) {
            set.remove(memoryId)
            false
        } else {
            set.add(memoryId)
            true
        }
        _savedMemoryIds.value = set
        return isSaved
    }

    fun addComment(comment: Comment) {
        val currentList = _commentsFlow.value[comment.memoryId] ?: emptyList()
        _commentsFlow.update { map ->
            map + (comment.memoryId to (currentList + comment))
        }
        _memoriesFlow.update { list ->
            list.map { mem ->
                if (mem.id == comment.memoryId) {
                    mem.copy(commentsCount = mem.commentsCount + 1)
                } else mem
            }
        }
    }

    fun deleteComment(memoryId: String, commentId: String) {
        val currentList = _commentsFlow.value[memoryId] ?: emptyList()
        _commentsFlow.update { map ->
            map + (memoryId to currentList.filterNot { it.id == commentId })
        }
        _memoriesFlow.update { list ->
            list.map { mem ->
                if (mem.id == memoryId) {
                    mem.copy(commentsCount = maxOf(0, mem.commentsCount - 1))
                } else mem
            }
        }
    }

    fun toggleFollow(targetUserId: String): Boolean {
        val set = _followingUserIds.value.toMutableSet()
        val isFollowing = if (set.contains(targetUserId)) {
            set.remove(targetUserId)
            false
        } else {
            set.add(targetUserId)
            true
        }
        _followingUserIds.value = set

        val current = _currentUserFlow.value
        if (current != null) {
            val updated = current.copy(
                followingCount = if (isFollowing) current.followingCount + 1 else maxOf(0, current.followingCount - 1)
            )
            saveUserSession(updated)
        }
        return isFollowing
    }

    fun updateUserProfile(
        userId: String,
        fullName: String,
        username: String,
        bio: String,
        profilePhoto: String?
    ) {
        val current = _currentUserFlow.value
        if (current != null && current.uid == userId) {
            val updated = current.copy(
                fullName = fullName,
                username = username,
                bio = bio,
                profilePhoto = profilePhoto ?: current.profilePhoto
            )
            saveUserSession(updated)
        }
    }

    fun markNotificationRead(notificationId: String) {
        _notificationsFlow.update { list ->
            list.map { if (it.id == notificationId) it.copy(read = true) else it }
        }
    }

    fun addActivity(activity: TimelineActivity) {
        _activitiesFlow.update { list ->
            listOf(activity) + list
        }
    }

    fun addCollection(collection: MemoryCollection) {
        _collectionsFlow.update { list ->
            listOf(collection) + list.filterNot { it.id == collection.id }
        }
    }

    fun deleteCollection(collectionId: String) {
        _collectionsFlow.update { list ->
            list.filterNot { it.id == collectionId }
        }
    }
}
