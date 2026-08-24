package com.example.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Memory
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GoMemoTopBar
import com.example.ui.components.MemoryCard
import com.example.ui.screens.timeline.TimelineScreen
import com.example.ui.screens.timeline.TimelineViewModel
import com.example.ui.theme.AccentLocation
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onEditProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMemoryClick: (String) -> Unit,
    onAddMemoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadProfilePhoto(context, uri)
        }
    }

    val user = state.userProfile

    Scaffold(
        topBar = {
            GoMemoTopBar(
                title = "Profile",
                showActions = false,
                navigationIcon = null
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
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("profile_content_list"),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Header Section
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar with change photo button
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                            ) {
                                AvatarImage(
                                    photoUrl = user?.profilePhoto,
                                    name = user?.fullName ?: "Explorer",
                                    size = 96,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (state.isUploadingPhoto) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(PurplePrimary)
                                            .border(2.dp, DarkBackground, CircleShape)
                                            .clickable { photoPickerLauncher.launch("image/*") }
                                            .align(Alignment.BottomEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Change photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Name & Username
                            Text(
                                text = user?.fullName?.ifBlank { "GoMemo Explorer" } ?: "Explorer",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )

                            if (!user?.username.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "@${user?.username}",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                                )
                            }

                            // Bio
                            if (!user?.bio.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = user?.bio ?: "",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextPrimary,
                                        lineHeight = 20.sp
                                    ),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Real Stats Row (Memories, Followers, Following, Places)
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ProfileStatItem(
                                        count = state.memories.size.toLong(),
                                        label = "Memories"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(30.dp)
                                            .background(BorderSubtle)
                                    )
                                    ProfileStatItem(
                                        count = user?.placesVisitedCount ?: 0,
                                        label = "Places"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(30.dp)
                                            .background(BorderSubtle)
                                    )
                                    ProfileStatItem(
                                        count = user?.followersCount ?: 0,
                                        label = "Followers"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(30.dp)
                                            .background(BorderSubtle)
                                    )
                                    ProfileStatItem(
                                        count = user?.followingCount ?: 0,
                                        label = "Following"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons: Edit Profile & Settings
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onEditProfileClick,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = DarkSurface,
                                        contentColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("button_edit_profile")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit Profile", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }

                                OutlinedButton(
                                    onClick = onSettingsClick,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = DarkSurface,
                                        contentColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("button_privacy_settings")
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Settings", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }

                    // Profile Navigation Tabs: Memories / Saved / Timeline
                    item {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(3.dp)
                            ) {
                                ProfileTabButton(
                                    text = "Memories",
                                    icon = Icons.Default.GridView,
                                    isSelected = state.activeTab == ProfileTab.MEMORIES,
                                    onClick = { viewModel.setTab(ProfileTab.MEMORIES) },
                                    modifier = Modifier.weight(1f)
                                )
                                ProfileTabButton(
                                    text = "Saved",
                                    icon = Icons.Default.Bookmark,
                                    isSelected = state.activeTab == ProfileTab.SAVED,
                                    onClick = { viewModel.setTab(ProfileTab.SAVED) },
                                    modifier = Modifier.weight(1f)
                                )
                                ProfileTabButton(
                                    text = "Timeline",
                                    icon = Icons.Default.Timeline,
                                    isSelected = state.activeTab == ProfileTab.TIMELINE,
                                    onClick = { viewModel.setTab(ProfileTab.TIMELINE) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    when (state.activeTab) {
                        ProfileTab.MEMORIES -> {
                            if (state.memories.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        title = "No memories yet.",
                                        subtitle = "Save places you visited, upload photos, and create your real timeline.",
                                        actionText = "Add your first memory",
                                        onActionClick = onAddMemoryClick,
                                        icon = Icons.Default.PhotoCamera,
                                        modifier = Modifier.padding(top = 20.dp)
                                    )
                                }
                            } else {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        state.memories.chunked(3).forEach { rowMemories ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                rowMemories.forEach { memory ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .aspectRatio(1f)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(DarkSurfaceElevated)
                                                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                                            .clickable { onMemoryClick(memory.id) }
                                                            .testTag("profile_memory_grid_item_${memory.id}")
                                                    ) {
                                                        if (memory.photoUrls.isNotEmpty()) {
                                                            AsyncImage(
                                                                model = memory.photoUrls.first(),
                                                                contentDescription = memory.placeName,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(8.dp),
                                                                verticalArrangement = Arrangement.Center,
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                            ) {
                                                                Icon(Icons.Default.Place, contentDescription = null, tint = AccentLocation, modifier = Modifier.size(22.dp))
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = memory.placeName,
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        color = TextPrimary,
                                                                        fontSize = 10.sp
                                                                    ),
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }

                                                        if (memory.visibility == "private") {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(20.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color.Black.copy(alpha = 0.7f))
                                                                    .align(Alignment.TopEnd)
                                                                    .padding(3.dp)
                                                            ) {
                                                                Icon(Icons.Default.Lock, contentDescription = "Private", tint = Color.White, modifier = Modifier.size(14.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                                repeat(3 - rowMemories.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ProfileTab.SAVED -> {
                            if (state.savedMemories.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        title = "No saved memories",
                                        subtitle = "Tap the bookmark icon on any memory in your feed to save it here for later.",
                                        icon = Icons.Default.Bookmark,
                                        modifier = Modifier.padding(top = 20.dp)
                                    )
                                }
                            } else {
                                items(state.savedMemories, key = { it.id }) { memory ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        MemoryCard(
                                            memory = memory,
                                            isLiked = false,
                                            onLikeClick = {},
                                            onCommentClick = { onMemoryClick(memory.id) },
                                            onAuthorClick = {},
                                            onLocationClick = {},
                                            onCardClick = { onMemoryClick(memory.id) },
                                            isSaved = true
                                        )
                                    }
                                }
                            }
                        }

                        ProfileTab.TIMELINE -> {
                            if (state.memories.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        title = "No timeline events",
                                        subtitle = "Start logging places you've visited to see your chronological journey.",
                                        actionText = "Add Memory",
                                        onActionClick = onAddMemoryClick,
                                        icon = Icons.Default.Timeline,
                                        modifier = Modifier.padding(top = 20.dp)
                                    )
                                }
                            } else {
                                items(state.memories.sortedByDescending { it.createdAt }, key = { it.id }) { memory ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        MemoryCard(
                                            memory = memory,
                                            isLiked = false,
                                            onLikeClick = {},
                                            onCommentClick = { onMemoryClick(memory.id) },
                                            onAuthorClick = {},
                                            onLocationClick = {},
                                            onCardClick = { onMemoryClick(memory.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileTabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) PurplePrimary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else TextSecondary,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
fun ProfileStatItem(count: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 11.sp
            )
        )
    }
}
