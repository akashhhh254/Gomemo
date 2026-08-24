package com.example.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Memory
import com.example.data.model.UserProfile
import com.example.data.repository.MemoryRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab {
    PLACES, TAGS, EXPLORERS
}

data class SearchUiState(
    val query: String = "",
    val activeTab: SearchTab = SearchTab.PLACES,
    val placeMemories: List<Memory> = emptyList(),
    val tagMemories: List<Memory> = emptyList(),
    val userProfiles: List<UserProfile> = emptyList(),
    val isLoading: Boolean = false,
    val popularTags: List<String> = emptyList()
)

class SearchViewModel(
    private val memoryRepository: MemoryRepository = MemoryRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadPopularTags()
    }

    private fun loadPopularTags() {
        viewModelScope.launch {
            memoryRepository.getPublicMemoriesFeed().collect { feed: List<Memory> ->
                val allTags: List<String> = feed.flatMap { it.tags }
                val tagCounts = mutableMapOf<String, Int>()
                for (tag in allTags) {
                    tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
                }
                val sortedTags = tagCounts.entries
                    .sortedByDescending { it.value }
                    .take(8)
                    .map { it.key }
                _uiState.update { it.copy(popularTags = sortedTags) }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        performSearch(newQuery)
    }

    fun setTab(tab: SearchTab) {
        _uiState.update { it.copy(activeTab = tab) }
        performSearch(_uiState.value.query)
    }

    fun selectTag(tag: String) {
        _uiState.update { it.copy(query = tag, activeTab = SearchTab.TAGS) }
        performSearch(tag)
    }

    private fun performSearch(query: String) {
        val q = query.trim().removePrefix("#")
        if (q.isBlank()) {
            _uiState.update {
                it.copy(
                    placeMemories = emptyList(),
                    tagMemories = emptyList(),
                    userProfiles = emptyList(),
                    isLoading = false
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (_uiState.value.activeTab) {
                SearchTab.PLACES -> {
                    val results = memoryRepository.searchMemoriesByPlace(q)
                    _uiState.update { it.copy(placeMemories = results, isLoading = false) }
                }
                SearchTab.TAGS -> {
                    val results = memoryRepository.searchMemoriesByPlace(q)
                    _uiState.update { it.copy(tagMemories = results, isLoading = false) }
                }
                SearchTab.EXPLORERS -> {
                    val results = userRepository.searchUsers(q)
                    _uiState.update { it.copy(userProfiles = results, isLoading = false) }
                }
            }
        }
    }
}
