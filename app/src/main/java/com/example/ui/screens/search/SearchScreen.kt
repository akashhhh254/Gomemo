package com.example.ui.screens.search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmptyStateView
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onMemoryClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
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
                        placeholder = { Text("Search places, tags, explorers...", color = TextSecondary, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderSubtle,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("search_main_input")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
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
            // Tab Selector (Places / Tags / Explorers)
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    SearchTabButton(
                        text = "Places",
                        icon = Icons.Default.Place,
                        isSelected = state.activeTab == SearchTab.PLACES,
                        onClick = { viewModel.setTab(SearchTab.PLACES) },
                        modifier = Modifier.weight(1f)
                    )
                    SearchTabButton(
                        text = "Tags",
                        icon = Icons.Default.Tag,
                        isSelected = state.activeTab == SearchTab.TAGS,
                        onClick = { viewModel.setTab(SearchTab.TAGS) },
                        modifier = Modifier.weight(1f)
                    )
                    SearchTabButton(
                        text = "Explorers",
                        icon = Icons.Default.People,
                        isSelected = state.activeTab == SearchTab.EXPLORERS,
                        onClick = { viewModel.setTab(SearchTab.EXPLORERS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Popular tags recommendation if query is blank
            if (state.query.isBlank() && state.popularTags.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Popular Tags to Explore",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.popularTags.forEach { tag ->
                            Surface(
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { viewModel.selectTag(tag) }
                            ) {
                                Text(
                                    text = if (tag.startsWith("#")) tag else "#$tag",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = PurplePrimaryLight,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PurplePrimary)
                    }
                } else if (state.query.isBlank()) {
                    EmptyStateView(
                        title = "Search GoMemo",
                        subtitle = "Find real places visited, explore hashtags, or discover explorers.",
                        icon = Icons.Default.Search,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    when (state.activeTab) {
                        SearchTab.PLACES -> {
                            if (state.placeMemories.isEmpty()) {
                                EmptyStateView(
                                    title = "No places found",
                                    subtitle = "No memories match '${state.query}'",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.testTag("search_places_list")
                                ) {
                                    items(state.placeMemories, key = { it.id }) { memory ->
                                        MemoryCard(
                                            memory = memory,
                                            isLiked = false,
                                            onLikeClick = {},
                                            onCommentClick = { onMemoryClick(memory.id) },
                                            onAuthorClick = { onUserClick(memory.userId) },
                                            onLocationClick = {},
                                            onCardClick = { onMemoryClick(memory.id) }
                                        )
                                    }
                                }
                            }
                        }
                        SearchTab.TAGS -> {
                            if (state.tagMemories.isEmpty()) {
                                EmptyStateView(
                                    title = "No tags found",
                                    subtitle = "No memories tagged with #${state.query}",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.testTag("search_tags_list")
                                ) {
                                    items(state.tagMemories, key = { it.id }) { memory ->
                                        MemoryCard(
                                            memory = memory,
                                            isLiked = false,
                                            onLikeClick = {},
                                            onCommentClick = { onMemoryClick(memory.id) },
                                            onAuthorClick = { onUserClick(memory.userId) },
                                            onLocationClick = {},
                                            onCardClick = { onMemoryClick(memory.id) }
                                        )
                                    }
                                }
                            }
                        }
                        SearchTab.EXPLORERS -> {
                            if (state.userProfiles.isEmpty()) {
                                EmptyStateView(
                                    title = "No explorers found",
                                    subtitle = "No users match '${state.query}'",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.testTag("search_users_list")
                                ) {
                                    items(state.userProfiles, key = { it.uid }) { profile ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(DarkSurface)
                                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                                .clickable { onUserClick(profile.uid) }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AvatarImage(photoUrl = profile.profilePhoto, name = profile.fullName, size = 44)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = profile.fullName,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary
                                                    )
                                                )
                                                if (profile.username.isNotBlank()) {
                                                    Text(
                                                        text = "@${profile.username}",
                                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                                    )
                                                }
                                                if (profile.bio.isNotBlank()) {
                                                    Text(
                                                        text = profile.bio,
                                                        style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${profile.memoriesCount} memories",
                                                style = MaterialTheme.typography.labelSmall.copy(color = PurplePrimaryLight)
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
}

@Composable
fun SearchTabButton(
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
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else TextSecondary
                )
            )
        }
    }
}
