package com.example.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.data.model.Story
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ExploreNearbyBanner
import com.example.ui.components.GoMemoTopBar
import com.example.ui.components.MemoryCard
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

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMemoryClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onLocationClick: (Double, Double, String) -> Unit,
    onAddMemoryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedStoryImageUri by remember { mutableStateOf<Uri?>(null) }
    var storyPlaceName by remember { mutableStateOf("") }
    var storyCaption by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedStoryImageUri = uri
            viewModel.setShowCreateStoryDialog(true)
        }
    }

    val filteredMemories = if (state.selectedTagFilter != null) {
        state.memories.filter { it.tags.contains(state.selectedTagFilter) }
    } else {
        state.memories
    }

    val availableTags = state.memories.flatMap { it.tags }.distinct().take(10)
    val currentUser = state.currentUserProfile
    val myStory = state.stories.find { it.userId == currentUser?.uid }

    Scaffold(
        topBar = {
            GoMemoTopBar(
                title = "GoMemo",
                hasUnreadNotifications = state.unreadNotificationsCount > 0,
                onSearchClick = onSearchClick,
                onNotificationsClick = onNotificationsClick
            )
        },
        containerColor = DarkBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            // Stories Horizontal Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Add / My Story Item
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            if (myStory != null) {
                                viewModel.openStoryViewer(myStory)
                            } else {
                                photoPickerLauncher.launch("image/*")
                            }
                        }
                        .testTag("button_my_story")
                ) {
                    Box(
                        modifier = Modifier.size(62.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val ringBrush = if (myStory != null) {
                            Brush.sweepGradient(listOf(PurplePrimary, PurplePrimaryLight, PurplePrimary))
                        } else {
                            Brush.linearGradient(listOf(BorderSubtle, BorderSubtle))
                        }

                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(ringBrush)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(DarkBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarImage(
                                photoUrl = currentUser?.profilePhoto ?: "",
                                name = currentUser?.fullName ?: "Me",
                                size = 54
                            )
                        }

                        // Plus badge
                        if (myStory == null) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(PurplePrimary)
                                    .border(1.5.dp, DarkBackground, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Story",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (myStory != null) "Your Story" else "Add Story",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }

                // Followed Stories
                state.stories.filter { it.userId != currentUser?.uid }.forEach { story ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { viewModel.openStoryViewer(story) }
                            .testTag("story_item_${story.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(listOf(PurplePrimary, PurplePrimaryLight, Color(0xFFE040FB), PurplePrimary))
                                )
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(DarkBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarImage(
                                photoUrl = story.userPhoto,
                                name = story.userName,
                                size = 54
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = story.userName.split(" ").firstOrNull() ?: story.userUsername,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(60.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Feed Switcher Tabs (Following / Explore)
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.activeFeedFilter == FeedFilter.FOLLOWING) DarkSurfaceElevated else Color.Transparent)
                            .clickable { viewModel.setFeedFilter(FeedFilter.FOLLOWING) }
                            .padding(vertical = 8.dp)
                            .testTag("tab_feed_following"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Following",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (state.activeFeedFilter == FeedFilter.FOLLOWING) FontWeight.Bold else FontWeight.Medium,
                                color = if (state.activeFeedFilter == FeedFilter.FOLLOWING) TextPrimary else TextSecondary,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.activeFeedFilter == FeedFilter.EXPLORE) DarkSurfaceElevated else Color.Transparent)
                            .clickable { viewModel.setFeedFilter(FeedFilter.EXPLORE) }
                            .padding(vertical = 8.dp)
                            .testTag("tab_feed_explore"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Explore Places",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (state.activeFeedFilter == FeedFilter.EXPLORE) FontWeight.Bold else FontWeight.Medium,
                                color = if (state.activeFeedFilter == FeedFilter.EXPLORE) TextPrimary else TextSecondary,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PurplePrimary)
                    }
                } else if (state.memories.isEmpty()) {
                    if (state.activeFeedFilter == FeedFilter.FOLLOWING) {
                        EmptyStateView(
                            title = "Follow people to see their memories here.",
                            subtitle = "Discover other explorers or share your own first memory.",
                            actionText = "Explore Discover Feed",
                            onActionClick = { viewModel.setFeedFilter(FeedFilter.EXPLORE) },
                            icon = Icons.Outlined.People,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        EmptyStateView(
                            title = "No memories to show yet.",
                            subtitle = "Be the first explorer to capture and share a real place memory on GoMemo!",
                            actionText = "Create Memory",
                            onActionClick = onAddMemoryClick,
                            icon = Icons.Outlined.PhotoCamera,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_feed_list"),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Tag filter chips row
                        if (availableTags.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = if (state.selectedTagFilter == null) PurplePrimary else DarkSurfaceVariant,
                                        shape = RoundedCornerShape(20.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { viewModel.setTagFilter(null) }
                                    ) {
                                        Text(
                                            text = "All Places",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = if (state.selectedTagFilter == null) TextPrimary else TextSecondary,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }

                                    availableTags.forEach { tag ->
                                        val isSelected = state.selectedTagFilter == tag
                                        Surface(
                                            color = if (isSelected) PurplePrimary else DarkSurfaceVariant,
                                            shape = RoundedCornerShape(20.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable { viewModel.setTagFilter(tag) }
                                        ) {
                                            Text(
                                                text = if (tag.startsWith("#")) tag else "#$tag",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = if (isSelected) TextPrimary else TextSecondary,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Memory Cards
                        items(filteredMemories, key = { it.id }) { memory ->
                            val isLiked = state.likedMemoryIds.contains(memory.id)
                            val isSaved = state.savedMemoryIds.contains(memory.id)

                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                MemoryCard(
                                    memory = memory,
                                    isLiked = isLiked,
                                    onLikeClick = { viewModel.toggleLike(memory) },
                                    onCommentClick = { onMemoryClick(memory.id) },
                                    onAuthorClick = { onAuthorClick(memory.userId) },
                                    onLocationClick = {
                                        onLocationClick(memory.latitude, memory.longitude, memory.placeName)
                                    },
                                    onCardClick = { onMemoryClick(memory.id) },
                                    isSaved = isSaved,
                                    onSaveClick = { viewModel.toggleSave(memory.id) },
                                    onShareClick = { viewModel.shareMemory(context, memory) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Story Viewer Overlay Dialog
        state.activeStoryToView?.let { story ->
            val isMyStory = story.userId == currentUser?.uid
            AlertDialog(
                onDismissRequest = { viewModel.closeStoryViewer() },
                containerColor = DarkSurface,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                title = null,
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarImage(photoUrl = story.userPhoto, name = story.userName, size = 38)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = story.userName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                if (story.placeName.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = PurplePrimaryLight, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(text = story.placeName, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                                    }
                                }
                            }
                            if (isMyStory) {
                                IconButton(onClick = { viewModel.deleteStory(story.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Story", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            IconButton(onClick = { viewModel.closeStoryViewer() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = story.photoUrl,
                                contentDescription = "Story Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (story.caption.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = story.caption,
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isMyStory && story.viewers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.Start)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${story.viewers.size} viewers",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                                )
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // Story Creator Dialog
        if (state.showCreateStoryDialog && selectedStoryImageUri != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!state.isPublishingStory) viewModel.setShowCreateStoryDialog(false)
                },
                containerColor = DarkSurface,
                title = {
                    Text("Publish Story (24h)", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = selectedStoryImageUri,
                                contentDescription = "Selected Story Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = storyPlaceName,
                            onValueChange = { storyPlaceName = it },
                            label = { Text("Place Tag (optional)") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = PurplePrimaryLight) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = storyCaption,
                            onValueChange = { storyCaption = it },
                            label = { Text("Caption (optional)") },
                            singleLine = false,
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedStoryImageUri?.let { uri ->
                                context.contentResolver.openInputStream(uri)?.let { stream ->
                                    viewModel.publishStory(stream, storyPlaceName, storyCaption) {
                                        selectedStoryImageUri = null
                                        storyPlaceName = ""
                                        storyCaption = ""
                                    }
                                }
                            }
                        },
                        enabled = !state.isPublishingStory,
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                    ) {
                        if (state.isPublishingStory) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Publish Story")
                        }
                    }
                },
                dismissButton = {
                    if (!state.isPublishingStory) {
                        TextButton(onClick = { viewModel.setShowCreateStoryDialog(false) }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                }
            )
        }
    }
}

