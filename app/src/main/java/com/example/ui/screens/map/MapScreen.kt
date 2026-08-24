package com.example.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.Memory
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmptyStateView
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

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onMemoryClick: (String) -> Unit,
    onAddMemoryWithLocation: (Double, Double, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initLocationHelper(context)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setLocationPermissionGranted(hasPermission)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationPermissionGranted(granted)
    }

    val displayedMemories = if (state.myMemoriesOnly && state.currentUserProfile != null) {
        state.memories.filter { it.userId == state.currentUserProfile?.uid }
    } else {
        state.memories
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Interactive Geo Map View
        InteractiveMapCanvas(
            centerLat = state.centerLatitude,
            centerLng = state.centerLongitude,
            memories = displayedMemories,
            selectedMemory = state.selectedMemory,
            userLocation = state.userLocation,
            onMarkerClick = { memory -> viewModel.selectMemory(memory) },
            onMapClick = { viewModel.selectMemory(null) }
        )

        // Top Search & Controls Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            // Search Bar
            Surface(
                color = DarkSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = PurplePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text("Search places or locations...", color = TextSecondary, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_input")
                    )
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                }
            }

            // Search Results Dropdown
            if (state.searchResults.isNotEmpty()) {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(state.searchResults) { place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectSearchResult(place) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = AccentLocation,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.placeName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                    )
                                    Text(
                                        text = place.address,
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Tabs (All Places vs My Places)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (!state.myMemoriesOnly) PurplePrimary else DarkSurface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { if (state.myMemoriesOnly) viewModel.toggleFilterMyMemories() }
                        .testTag("filter_all_places")
                ) {
                    Text(
                        text = "All Memories (${state.memories.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (!state.myMemoriesOnly) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                Surface(
                    color = if (state.myMemoriesOnly) PurplePrimary else DarkSurface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { if (!state.myMemoriesOnly) viewModel.toggleFilterMyMemories() }
                        .testTag("filter_my_places")
                ) {
                    Text(
                        text = "My Places",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (state.myMemoriesOnly) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Floating GPS Location button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (state.selectedMemory != null || state.selectedCustomLocation != null) 240.dp else 24.dp, end = 16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (state.hasLocationPermission) {
                            viewModel.fetchUserLocation()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                    .testTag("map_gps_center_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = if (state.hasLocationPermission) AccentLocation else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Bottom Selected Memory Details Card
        AnimatedVisibility(
            visible = state.selectedMemory != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            state.selectedMemory?.let { memory ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMemoryClick(memory.id) }
                        .testTag("map_memory_preview_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Photo preview
                        if (memory.photoUrls.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceElevated)
                            ) {
                                AsyncImage(
                                    model = memory.photoUrls.first(),
                                    contentDescription = memory.placeName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        // Info Column
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = memory.placeName.ifBlank { "Saved Place" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = listOfNotNull(
                                        memory.dateVisited.ifBlank { null },
                                        memory.timeVisited.ifBlank { null }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "By ${memory.authorName}",
                                style = MaterialTheme.typography.labelSmall.copy(color = PurplePrimaryLight),
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { onMemoryClick(memory.id) },
                            modifier = Modifier.testTag("map_open_memory_details_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "View details",
                                tint = PurplePrimary
                            )
                        }
                    }
                }
            }
        }

        // Custom Location Selected Card (Add Memory Here)
        AnimatedVisibility(
            visible = state.selectedCustomLocation != null && state.selectedMemory == null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            state.selectedCustomLocation?.let { loc ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = AccentLocation,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = loc.placeName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = loc.address,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.clearCustomLocation() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onAddMemoryWithLocation(loc.latitude, loc.longitude, loc.placeName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("button_add_memory_at_location")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Add Memory At This Place",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveMapCanvas(
    centerLat: Double,
    centerLng: Double,
    memories: List<Memory>,
    selectedMemory: Memory?,
    userLocation: android.location.Location?,
    onMarkerClick: (Memory) -> Unit,
    onMapClick: () -> Unit
) {
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                    zoomScale = (zoomScale * zoom).coerceIn(0.5f, 4.0f)
                }
            }
            .clickable { onMapClick() }
            .testTag("interactive_map_canvas")
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Dark map aesthetic background grid & topography lines
            drawRect(color = Color(0xFF0A0A0A))

            // Geographic grid grid lines
            val gridSpacing = 80.dp.toPx() * zoomScale
            val startX = (panOffsetX % gridSpacing)
            val startY = (panOffsetY % gridSpacing)

            var x = startX
            while (x < width) {
                drawLine(
                    color = Color(0x14FFFFFF),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
                x += gridSpacing
            }

            var y = startY
            while (y < height) {
                drawLine(
                    color = Color(0x14FFFFFF),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                y += gridSpacing
            }

            // Decorative road curves
            val roadPath = Path().apply {
                moveTo(width * 0.1f + panOffsetX * 0.5f, 0f)
                cubicTo(
                    width * 0.4f + panOffsetX, height * 0.3f + panOffsetY,
                    width * 0.2f + panOffsetX, height * 0.7f + panOffsetY,
                    width * 0.9f + panOffsetX, height
                )
            }
            drawPath(
                path = roadPath,
                color = Color(0x1FA855F7),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
            )

            // Draw GPS User Location pulse if available
            if (userLocation != null) {
                val userX = width / 2 + (userLocation.longitude.toFloat() - centerLng.toFloat()) * 3000f * zoomScale + panOffsetX
                val userY = height / 2 - (userLocation.latitude.toFloat() - centerLat.toFloat()) * 3000f * zoomScale + panOffsetY

                drawCircle(
                    color = AccentLocation.copy(alpha = 0.2f),
                    radius = 24.dp.toPx(),
                    center = Offset(userX, userY)
                )
                drawCircle(
                    color = AccentLocation,
                    radius = 7.dp.toPx(),
                    center = Offset(userX, userY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(userX, userY)
                )
            }
        }

        // Render Memory Markers as interactive Composables over canvas
        memories.forEach { memory ->
            val memX = width / 2 + (memory.longitude.toFloat() - centerLng.toFloat()) * 3000f * zoomScale + panOffsetX
            val memY = height / 2 - (memory.latitude.toFloat() - centerLat.toFloat()) * 3000f * zoomScale + panOffsetY

            // Only render if roughly within screen bounds
            if (memX in -100f..(width + 100f) && memY in -100f..(height + 100f)) {
                val isSelected = selectedMemory?.id == memory.id
                Box(
                    modifier = Modifier
                        .offset { IntOffset(memX.toInt() - 24.dp.roundToPx(), memY.toInt() - 48.dp.roundToPx()) }
                        .clickable { onMarkerClick(memory) }
                        .testTag("map_marker_${memory.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    MemoryMapPin(memory = memory, isSelected = isSelected)
                }
            }
        }
    }
}

@Composable
fun MemoryMapPin(
    memory: Memory,
    isSelected: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (isSelected) 46.dp else 38.dp)
                .clip(CircleShape)
                .background(if (isSelected) PurplePrimary else DarkSurfaceElevated)
                .border(
                    width = if (isSelected) 2.dp else 1.5.dp,
                    color = if (isSelected) Color.White else PurplePrimaryLight,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (memory.photoUrls.isNotEmpty()) {
                AsyncImage(
                    model = memory.photoUrls.first(),
                    contentDescription = memory.placeName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else PurplePrimaryLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Pin stem / arrow
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(6.dp)
                .background(if (isSelected) Color.White else PurplePrimary)
        )
    }
}
