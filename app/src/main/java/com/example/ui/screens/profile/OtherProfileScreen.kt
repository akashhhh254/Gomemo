package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FollowStatus
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmptyStateView
import com.example.ui.components.MemoryCard
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.PurplePrimaryLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(
    viewModel: OtherProfileViewModel,
    onNavigateBack: () -> Unit,
    onMemoryClick: (String) -> Unit,
    onOpenChat: ((chatId: String, recipientId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val user = state.targetUser
    var showMenu by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = user?.username?.let { "@$it" } ?: "Explorer Profile",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = TextPrimary)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(DarkSurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Report Profile", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = TextSecondary) },
                            onClick = {
                                showMenu = false
                                showReportDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Block Explorer", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showBlockDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            } else if (user == null) {
                EmptyStateView(
                    title = "User not found",
                    subtitle = "This explorer account may have been removed.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val isPrivateLocked = user.isPrivateAccount && state.followStatus != FollowStatus.FOLLOWING

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("other_profile_list"),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Header
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AvatarImage(
                                photoUrl = user.profilePhoto,
                                name = user.fullName,
                                size = 88
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = user.fullName.ifBlank { "GoMemo Explorer" },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )

                            if (user.username.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "@${user.username}",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                                )
                            }

                            if (user.bio.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = user.bio,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextPrimary,
                                        lineHeight = 20.sp
                                    ),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Stats
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
                                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(BorderSubtle))
                                    ProfileStatItem(
                                        count = user.placesVisitedCount,
                                        label = "Places"
                                    )
                                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(BorderSubtle))
                                    ProfileStatItem(
                                        count = user.followersCount,
                                        label = "Followers"
                                    )
                                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(BorderSubtle))
                                    ProfileStatItem(
                                        count = user.followingCount,
                                        label = "Following"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Actions: Follow & Message
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.toggleFollow() },
                                    enabled = !state.isFollowActionInProgress,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when (state.followStatus) {
                                            FollowStatus.FOLLOWING -> DarkSurfaceElevated
                                            FollowStatus.REQUESTED -> DarkSurfaceElevated
                                            else -> PurplePrimary
                                        },
                                        contentColor = when (state.followStatus) {
                                            FollowStatus.FOLLOWING -> TextPrimary
                                            FollowStatus.REQUESTED -> PurplePrimaryLight
                                            else -> Color.White
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("button_follow_user")
                                ) {
                                    if (state.isFollowActionInProgress) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val icon = when (state.followStatus) {
                                                FollowStatus.FOLLOWING -> Icons.Default.PersonRemove
                                                FollowStatus.REQUESTED -> Icons.Default.HourglassTop
                                                else -> Icons.Default.PersonAdd
                                            }
                                            val label = when (state.followStatus) {
                                                FollowStatus.FOLLOWING -> "Following"
                                                FollowStatus.REQUESTED -> "Requested"
                                                else -> if (user.isPrivateAccount) "Request Follow" else "Follow"
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }

                                if (onOpenChat != null) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.startChat { chatId, recipientId ->
                                                onOpenChat(chatId, recipientId)
                                            }
                                        },
                                        enabled = !state.isStartingChat,
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = DarkSurface,
                                            contentColor = PurplePrimaryLight
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("button_message_user")
                                    ) {
                                        if (state.isStartingChat) {
                                            CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.ChatBubbleOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Message",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isPrivateLocked) {
                        item {
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = PurplePrimaryLight,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "This Account is Private",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Follow this account to see their places and memories.",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "Memories",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }

                        if (state.memories.isEmpty()) {
                            item {
                                EmptyStateView(
                                    title = "No memories shared yet.",
                                    subtitle = "${user.fullName} hasn't shared any memories yet.",
                                    modifier = Modifier.padding(top = 20.dp)
                                )
                            }
                        } else {
                            items(state.memories, key = { it.id }) { memory ->
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

        // Block confirmation dialog
        if (showBlockDialog) {
            AlertDialog(
                onDismissRequest = { showBlockDialog = false },
                containerColor = DarkSurface,
                title = { Text("Block ${user?.fullName}?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "They won't be able to see your memories, follow your profile, or message you. You will also unfollow them.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBlockDialog = false
                            viewModel.blockUser(onNavigateBack)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Block", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        // Report dialog
        if (showReportDialog) {
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                containerColor = DarkSurface,
                title = { Text("Report Profile", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Please specify why you are reporting this account:", color = TextSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = reportReason,
                            onValueChange = { reportReason = it },
                            placeholder = { Text("e.g. Inappropriate content, spam, harassment...") },
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
                            if (reportReason.isNotBlank()) {
                                viewModel.reportUser(reportReason.trim())
                                showReportDialog = false
                                reportReason = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                    ) {
                        Text("Submit Report")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReportDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

