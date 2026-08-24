package com.example.ui.screens.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ActivityType
import com.example.data.model.TimelineActivity
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GoMemoTopBar
import com.example.ui.theme.AccentHeart
import com.example.ui.theme.AccentLocation
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.PurplePrimaryLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onMemoryClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            GoMemoTopBar(
                title = "Timeline",
                showActions = true,
                onSearchClick = onSearchClick,
                onNotificationsClick = onNotificationsClick
            )
        },
        containerColor = DarkBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PurplePrimary)
                }
            } else if (state.activities.isEmpty()) {
                EmptyStateView(
                    title = "No timeline events yet.",
                    subtitle = "Your real actions like adding memories, visiting places, following explorers, and liking memories will appear here.",
                    icon = Icons.Outlined.Timeline,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("timeline_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.activities, key = { it.id }) { activity ->
                        TimelineActivityCard(
                            activity = activity,
                            onMemoryClick = { if (activity.memoryId.isNotBlank()) onMemoryClick(activity.memoryId) },
                            onUserClick = { if (activity.userId.isNotBlank()) onUserClick(activity.userId) },
                            onTargetUserClick = { if (activity.targetUserId.isNotBlank()) onUserClick(activity.targetUserId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineActivityCard(
    activity: TimelineActivity,
    onMemoryClick: () -> Unit,
    onUserClick: () -> Unit,
    onTargetUserClick: () -> Unit
) {
    val (badgeIcon, badgeColor, badgeBg) = when (activity.type) {
        ActivityType.ADDED_MEMORY.name -> Triple(Icons.Default.PhotoCamera, PurplePrimaryLight, DarkSurfaceVariant)
        ActivityType.VISITED_PLACE.name -> Triple(Icons.Default.LocationOn, AccentLocation, DarkSurfaceVariant)
        ActivityType.FOLLOWED_USER.name, ActivityType.FOLLOWER_GAINED.name ->
            Triple(Icons.Default.PersonAdd, PurplePrimary, DarkSurfaceVariant)
        ActivityType.LIKED_MEMORY.name -> Triple(Icons.Default.Favorite, AccentHeart, DarkSurfaceVariant)
        ActivityType.COMMENTED_MEMORY.name -> Triple(Icons.Default.ChatBubble, PurplePrimaryLight, DarkSurfaceVariant)
        else -> Triple(Icons.Default.LocationOn, PurplePrimary, DarkSurfaceVariant)
    }

    val timeFormatted = activity.createdAt?.let {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(it)
    } ?: "Recent"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (activity.memoryId.isNotBlank()) onMemoryClick()
                else if (activity.targetUserId.isNotBlank()) onTargetUserClick()
            }
            .testTag("timeline_item_${activity.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Activity type icon badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(badgeBg)
                    .border(1.dp, BorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activity.title.ifBlank { "Activity" },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = activity.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                )
            }

            // Thumbnail photo preview if attached
            if (activity.memoryPhoto.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated)
                ) {
                    AsyncImage(
                        model = activity.memoryPhoto,
                        contentDescription = "Activity thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
