package com.example.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TimelineActivity
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimelineUiState(
    val activities: List<TimelineActivity> = emptyList(),
    val isLoading: Boolean = true,
    val currentUserProfile: UserProfile? = null
)

class TimelineViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        loadActivities()
    }

    private fun loadActivities() {
        val currentUid = authRepository.currentUserId
        if (currentUid.isNotBlank()) {
            viewModelScope.launch {
                userRepository.getUserProfile(currentUid).collect { profile ->
                    _uiState.update { it.copy(currentUserProfile = profile) }
                }
            }
            viewModelScope.launch {
                userRepository.getTimelineActivities(currentUid).collect { list ->
                    _uiState.update { it.copy(activities = list, isLoading = false) }
                }
            }
        } else {
            viewModelScope.launch {
                userRepository.getTimelineActivities("").collect { list ->
                    _uiState.update { it.copy(activities = list, isLoading = false) }
                }
            }
        }
    }
}
