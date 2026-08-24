package com.example.ui.screens.discover

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Memory
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmptyStateView
import com.example.ui.screens.map.MapScreen
import com.example.ui.screens.map.MapViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    mapViewModel: MapViewModel,
    onMemoryClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onAddMemoryWithLocation: (Double, Double, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        placeholder = {
                            Text("Search places, explorers, tags...", color = TextSecondary, fontSize = 13.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderSubtle,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("discover_search_input")
                    )
                },
                actions = {
                    // Toggle Grid / Map
                    IconButton(
                        onClick = {
                            val next = if (state.viewMode == DiscoverViewMode.GRID) DiscoverViewMode.MAP else DiscoverViewMode.GRID
                            viewModel.setViewMode(next)
                        },
                        modifier = Modifier.testTag("toggle_discover_view_mode")
                    ) {
                        Icon(
                            imageVector = if (state.viewMode == DiscoverViewMode.GRID) Icons.Default.Map else Icons.Default.GridView,
                            contentDescription = "Toggle Map / Grid",
                            tint = PurplePrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
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
            // Popular Tags Carousel
            if (state.popularTags.isNotEmpty() && state.query.isBlank() && state.viewMode == DiscoverViewMode.GRID) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.popularTags.forEach { tag ->
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.onQueryChange(tag) }
                        ) {
                            Text(
                                text = if (tag.startsWith("#")) tag else "#$tag",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = PurplePrimaryLight,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (state.viewMode == DiscoverViewMode.MAP) {
                    MapScreen(
                        viewModel = mapViewModel,
                        onMemoryClick = onMemoryClick,
                        onAddMemoryWithLocation = onAddMemoryWithLocation
                    )
                } else {
                    if (state.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PurplePrimary)
                        }
                    } else if (state.publicMemories.isEmpty()) {
                        EmptyStateView(
                            title = "No memories to discover yet.",
                            subtitle = "Be the first explorer to share a real location on GoMemo!",
                            icon = Icons.Default.LocationOn,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (state.matchingMemories.isEmpty() && state.searchedUsers.isEmpty()) {
                        EmptyStateView(
                            title = "No results found",
                            subtitle = "Try searching for another place, tag, or explorer username.",
                            icon = Icons.Default.Search,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Matching Explorers Row
                            if (state.searchedUsers.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Explorers",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                    )
                                }

                                items(state.searchedUsers, key = { it.uid }) { user ->
                                    val isFollowing = state.followingUserIds.contains(user.uid)
                                    Surface(
                                        color = DarkSurface,
                                        shape = RoundedCornerShape(14.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onUserClick(user.uid) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AvatarImage(photoUrl = user.profilePhoto, name = user.fullName, size = 44)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = user.fullName,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary
                                                    )
                                                )
                                                if (user.username.isNotBlank()) {
                                                    Text(
                                                        text = "@${user.username}",
                                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                                    )
                                                }
                                                Text(
                                                    text = "${user.memoriesCount} memories • ${user.placesVisitedCount} places",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontSize = 10.sp)
                                                )
                                            }

                                            if (isFollowing) {
                                                OutlinedButton(
                                                    onClick = { viewModel.toggleFollow(user.uid) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Following", fontSize = 12.sp)
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.toggleFollow(user.uid) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Follow", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Memories",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // 2-Column Staggered Visual Grid
                            item {
                                val memories = state.matchingMemories
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (i in memories.indices step 2) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val first = memories[i]
                                            DiscoverMemoryGridItem(
                                                memory = first,
                                                onClick = { onMemoryClick(first.id) },
                                                modifier = Modifier.weight(1f)
                                            )

                                            if (i + 1 < memories.size) {
                                                val second = memories[i + 1]
                                                DiscoverMemoryGridItem(
                                                    memory = second,
                                                    onClick = { onMemoryClick(second.id) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
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
    }
}

@Composable
fun DiscoverMemoryGridItem(
    memory: Memory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .aspectRatio(0.88f)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("discover_memory_${memory.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (memory.photoUrls.isNotEmpty()) {
                AsyncImage(
                    model = memory.photoUrls.first(),
                    contentDescription = memory.placeName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = PurplePrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x33000000),
                                Color(0xD9000000)
                            )
                        )
                    )
            )

            // Info overlay at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = memory.placeName.ifBlank { "Explorer Memory" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = memory.authorName.ifBlank { "Explorer" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${memory.likesCount}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
