package com.example.ui.screens.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Memory
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.StoryRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream

enum class FeedFilter {
    FOLLOWING,
    EXPLORE
}

data class HomeUiState(
    val memories: List<Memory> = emptyList(),
    val stories: List<Story> = emptyList(),
    val likedMemoryIds: Set<String> = emptySet(),
    val savedMemoryIds: Set<String> = emptySet(),
    val currentUserProfile: UserProfile? = null,
    val isLoading: Boolean = true,
    val isPublishingStory: Boolean = false,
    val activeStoryToView: Story? = null,
    val showCreateStoryDialog: Boolean = false,
    val unreadNotificationsCount: Int = 0,
    val selectedTagFilter: String? = null,
    val activeFeedFilter: FeedFilter = FeedFilter.FOLLOWING
)

class HomeViewModel(
    private val memoryRepository: MemoryRepository = MemoryRepository(),
    private val storyRepository: StoryRepository = StoryRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserData()
        loadStories()
        loadFeed(FeedFilter.FOLLOWING)
    }

    private fun loadUserData() {
        val currentUid = authRepository.currentUserId
        if (currentUid.isNotBlank()) {
            viewModelScope.launch {
                userRepository.getUserProfile(currentUid).collect { profile ->
                    _uiState.update { current ->
                        current.copy(
                            currentUserProfile = profile,
                            savedMemoryIds = profile?.savedMemories?.toSet() ?: emptySet()
                        )
                    }
                }
            }
            viewModelScope.launch {
                userRepository.getNotifications(currentUid).collect { notifs ->
                    val unread = notifs.count { !it.read }
                    _uiState.update { it.copy(unreadNotificationsCount = unread) }
                }
            }
        }
    }

    private fun loadStories() {
        val currentUid = authRepository.currentUserId
        if (currentUid.isNotBlank()) {
            viewModelScope.launch {
                storyRepository.getActiveStories(currentUid).collect { storiesList ->
                    _uiState.update { it.copy(stories = storiesList) }
                }
            }
        }
    }

    fun setShowCreateStoryDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateStoryDialog = show) }
    }

    fun openStoryViewer(story: Story) {
        _uiState.update { it.copy(activeStoryToView = story) }
        val currentUid = authRepository.currentUserId
        if (currentUid.isNotBlank()) {
            viewModelScope.launch {
                storyRepository.markStoryViewed(story.id, currentUid)
            }
        }
    }

    fun closeStoryViewer() {
        _uiState.update { it.copy(activeStoryToView = null) }
    }

    fun publishStory(
        inputStream: InputStream,
        placeName: String,
        caption: String,
        onSuccess: () -> Unit
    ) {
        val user = _uiState.value.currentUserProfile ?: return
        _uiState.update { it.copy(isPublishingStory = true) }

        viewModelScope.launch {
            val uploadResult = storyRepository.uploadStoryPhoto(user.uid, inputStream)
            uploadResult.fold(
                onSuccess = { photoUrl ->
                    val pubResult = storyRepository.publishStory(user, photoUrl, placeName, caption)
                    pubResult.fold(
                        onSuccess = {
                            _uiState.update { it.copy(isPublishingStory = false, showCreateStoryDialog = false) }
                            onSuccess()
                        },
                        onFailure = {
                            _uiState.update { it.copy(isPublishingStory = false) }
                        }
                    )
                },
                onFailure = {
                    _uiState.update { it.copy(isPublishingStory = false) }
                }
            )
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            storyRepository.deleteStory(storyId)
            closeStoryViewer()
        }
    }

    fun setFeedFilter(filter: FeedFilter) {
        if (_uiState.value.activeFeedFilter != filter) {
            _uiState.update { it.copy(activeFeedFilter = filter, isLoading = true) }
            loadFeed(filter)
        }
    }

    private fun loadFeed(filter: FeedFilter) {
        val currentUid = authRepository.currentUserId
        viewModelScope.launch {
            val feedFlow = if (filter == FeedFilter.FOLLOWING && currentUid.isNotBlank()) {
                memoryRepository.getFollowingFeed(currentUid)
            } else {
                memoryRepository.getPublicMemoriesFeed()
            }

            feedFlow.collect { memoriesList ->
                _uiState.update { it.copy(memories = memoriesList, isLoading = false) }

                // Collect like status for each memory
                if (currentUid.isNotBlank()) {
                    memoriesList.forEach { memory ->
                        launch {
                            memoryRepository.isMemoryLikedByUser(memory.id, currentUid).collect { isLiked ->
                                _uiState.update { current ->
                                    val newLikes = current.likedMemoryIds.toMutableSet()
                                    if (isLiked) newLikes.add(memory.id) else newLikes.remove(memory.id)
                                    current.copy(likedMemoryIds = newLikes)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun toggleLike(memory: Memory) {
        val user = _uiState.value.currentUserProfile ?: return
        viewModelScope.launch {
            memoryRepository.toggleLike(
                memory = memory,
                userId = user.uid,
                userName = user.fullName,
                userPhoto = user.profilePhoto
            )
        }
    }

    fun toggleSave(memoryId: String) {
        val uid = authRepository.currentUserId
        if (uid.isBlank() || memoryId.isBlank()) return

        val isSaved = _uiState.value.savedMemoryIds.contains(memoryId)
        viewModelScope.launch {
            if (isSaved) {
                memoryRepository.unsaveMemory(memoryId, uid)
            } else {
                memoryRepository.saveMemory(memoryId, uid)
            }
        }
    }

    fun shareMemory(context: Context, memory: Memory) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "Check out this memory from ${memory.placeName} on GoMemo: \"${memory.caption}\""
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Memory")
        context.startActivity(shareIntent)
    }

    fun setTagFilter(tag: String?) {
        _uiState.update {
            it.copy(selectedTagFilter = if (it.selectedTagFilter == tag) null else tag)
        }
    }
}

