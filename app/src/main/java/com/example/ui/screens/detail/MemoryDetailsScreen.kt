package com.example.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Comment
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmptyStateView
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailsScreen(
    viewModel: MemoryDetailsViewModel,
    onNavigateBack: () -> Unit,
    onAuthorClick: (String) -> Unit,
    onLocationClick: (Double, Double, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val memory = state.memory
    val isAuthor = state.currentUserProfile?.uid == memory?.userId
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = memory?.placeName?.ifBlank { "Memory Details" } ?: "Memory",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (isAuthor) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("button_delete_memory")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete memory",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            // Comment input box
            Surface(
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.newCommentText,
                        onValueChange = { viewModel.onCommentTextChange(it) },
                        placeholder = { Text("Write a comment...", color = TextSecondary, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurplePrimary,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_memory_comment")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.addComment() },
                        enabled = state.newCommentText.isNotBlank() && !state.isSendingComment,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (state.newCommentText.isNotBlank()) PurplePrimary else DarkSurfaceVariant)
                            .testTag("button_send_comment")
                    ) {
                        if (state.isSendingComment) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send comment",
                                tint = if (state.newCommentText.isNotBlank()) Color.White else TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PurplePrimary)
                }
            } else if (memory == null) {
                EmptyStateView(
                    title = "Memory not found",
                    subtitle = "This memory may have been removed.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("memory_details_content"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    // Photos carousel/scroll
                    if (memory.photoUrls.isNotEmpty()) {
                        item {
                            if (memory.photoUrls.size == 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(4f / 3f)
                                        .background(DarkSurfaceElevated)
                                ) {
                                    AsyncImage(
                                        model = memory.photoUrls.first(),
                                        contentDescription = memory.placeName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 8.dp, horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    memory.photoUrls.forEach { url ->
                                        Box(
                                            modifier = Modifier
                                                .size(260.dp, 200.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(DarkSurfaceElevated)
                                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                                        ) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = memory.placeName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Author Row & Place Metadata
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            // Author Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAuthorClick(memory.userId) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarImage(
                                    photoUrl = memory.authorPhoto,
                                    name = memory.authorName,
                                    size = 44
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = memory.authorName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    if (memory.authorUsername.isNotBlank()) {
                                        Text(
                                            text = "@${memory.authorUsername}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                        )
                                    }
                                }

                                if (memory.visibility == "private") {
                                    Surface(
                                        color = DarkSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Private", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Place & Location
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable {
                                        onLocationClick(memory.latitude, memory.longitude, memory.placeName)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = AccentLocation,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = memory.placeName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    if (memory.locationName.isNotBlank() && memory.locationName != memory.placeName) {
                                        Text(
                                            text = memory.locationName,
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                        )
                                    }
                                }
                                if (memory.latitude != 0.0 && memory.longitude != 0.0) {
                                    Text(
                                        text = "View on Map",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = PurplePrimaryLight,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            // Date & Time
                            if (memory.dateVisited.isNotBlank() || memory.timeVisited.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = listOfNotNull(
                                            memory.dateVisited.ifBlank { null },
                                            memory.timeVisited.ifBlank { null }
                                        ).joinToString(" at "),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = TextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }

                            // Caption
                            if (memory.caption.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = memory.caption,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = TextPrimary,
                                        lineHeight = 22.sp
                                    )
                                )
                            }

                            // Tags
                            if (memory.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    memory.tags.forEach { tag ->
                                        Surface(
                                            color = DarkSurfaceVariant,
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                        ) {
                                            Text(
                                                text = if (tag.startsWith("#")) tag else "#$tag",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = PurplePrimaryLight,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Likes / Comments interactive count row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.toggleLike() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("button_detail_like"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (state.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = if (state.isLiked) AccentHeart else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${memory.likesCount} ${if (memory.likesCount == 1L) "Like" else "Likes"}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = if (state.isLiked) AccentHeart else TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = "Comments",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${state.comments.size} Comments",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = TextSecondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Comments (${state.comments.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                    }

                    // Comments List
                    if (state.comments.isEmpty()) {
                        item {
                            Text(
                                text = "No comments yet. Be the first to leave a comment!",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        items(state.comments, key = { it.id }) { comment ->
                            val isCommentAuthor = state.currentUserProfile?.uid == comment.userId
                            val commentTime = comment.createdAt?.let {
                                SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(it)
                            } ?: "Just now"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                AvatarImage(
                                    photoUrl = comment.userPhoto,
                                    name = comment.userName,
                                    size = 36,
                                    modifier = Modifier.clickable { onAuthorClick(comment.userId) }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = comment.userName,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        )
                                        Text(
                                            text = commentTime,
                                            style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontSize = 11.sp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = comment.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, lineHeight = 18.sp)
                                    )
                                }

                                if (isCommentAuthor) {
                                    IconButton(
                                        onClick = { viewModel.deleteComment(comment.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete comment", tint = TextTertiary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = DarkSurface,
                title = { Text("Delete Memory?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to permanently delete this memory and its photos?", color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteMemory {
                                onNavigateBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}
