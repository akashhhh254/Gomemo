package com.example.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Memory
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiscoverViewMode {
    GRID,
    MAP
}

data class DiscoverUiState(
    val query: String = "",
    val viewMode: DiscoverViewMode = DiscoverViewMode.GRID,
    val publicMemories: List<Memory> = emptyList(),
    val searchedUsers: List<UserProfile> = emptyList(),
    val matchingMemories: List<Memory> = emptyList(),
    val popularTags: List<String> = emptyList(),
    val followingUserIds: Set<String> = emptySet(),
    val isLoading: Boolean = true
)

class DiscoverViewModel(
    private val memoryRepository: MemoryRepository = MemoryRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val currentUid = authRepository.currentUserId
        if (currentUid.isNotBlank()) {
            viewModelScope.launch {
                userRepository.getUserProfile(currentUid).collect { profile ->
                    _uiState.update { it.copy(followingUserIds = profile?.following?.toSet() ?: emptySet()) }
                }
            }
        }

        viewModelScope.launch {
            memoryRepository.getPublicMemoriesFeed().collect { memories ->
                val tags = memories.flatMap { it.tags }.distinct().take(12)
                _uiState.update {
                    it.copy(
                        publicMemories = memories,
                        popularTags = tags,
                        isLoading = false
                    )
                }
                filterData(_uiState.value.query)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        filterData(newQuery)
    }

    fun setViewMode(mode: DiscoverViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    private fun filterData(q: String) {
        val trimmed = q.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(matchingMemories = it.publicMemories, searchedUsers = emptyList())
            }
            return
        }

        val cleanTag = trimmed.removePrefix("#")
        val filtered = _uiState.value.publicMemories.filter { mem ->
            mem.placeName.contains(trimmed, ignoreCase = true) ||
            mem.locationName.contains(trimmed, ignoreCase = true) ||
            mem.city.contains(trimmed, ignoreCase = true) ||
            mem.country.contains(trimmed, ignoreCase = true) ||
            mem.tags.any { it.contains(cleanTag, ignoreCase = true) } ||
            mem.caption.contains(trimmed, ignoreCase = true)
        }

        viewModelScope.launch {
            val users = userRepository.searchUsers(trimmed)
            val filteredUsers = users.filter { it.uid != authRepository.currentUserId }
            _uiState.update {
                it.copy(
                    matchingMemories = filtered,
                    searchedUsers = filteredUsers
                )
            }
        }
    }

    fun toggleFollow(targetUserId: String) {
        val currentUid = authRepository.currentUserId
        if (currentUid.isBlank() || targetUserId.isBlank()) return

        val isFollowing = _uiState.value.followingUserIds.contains(targetUserId)
        viewModelScope.launch {
            if (isFollowing) {
                userRepository.unfollowUser(currentUid, targetUserId)
            } else {
                userRepository.followUser(currentUid, targetUserId)
            }
        }
    }
}
