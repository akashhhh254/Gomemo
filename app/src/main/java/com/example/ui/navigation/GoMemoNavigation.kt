package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.GoMemoBottomBar
import com.example.ui.components.NavigationTab
import com.example.ui.screens.add.AddMemoryScreen
import com.example.ui.screens.add.AddMemoryViewModel
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.chat.ChatDetailScreen
import com.example.ui.screens.chat.ChatDetailViewModel
import com.example.ui.screens.chat.MessagesScreen
import com.example.ui.screens.chat.MessagesViewModel
import com.example.ui.screens.detail.MemoryDetailsScreen
import com.example.ui.screens.detail.MemoryDetailsViewModel
import com.example.ui.screens.discover.DiscoverScreen
import com.example.ui.screens.discover.DiscoverViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.map.MapViewModel
import com.example.ui.screens.notifications.NotificationsScreen
import com.example.ui.screens.notifications.NotificationsViewModel
import com.example.ui.screens.profile.EditProfileScreen
import com.example.ui.screens.profile.OtherProfileScreen
import com.example.ui.screens.profile.OtherProfileViewModel
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.profile.ProfileViewModel
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.search.SearchViewModel
import com.example.ui.theme.DarkBackground
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val AUTH = "auth"
    const val MAIN = "main"
    const val DIAGNOSTICS = "diagnostics"

    const val EDIT_PROFILE = "edit_profile"
    const val OTHER_PROFILE = "other_profile/{userId}"
    const val MEMORY_DETAIL = "memory_detail/{memoryId}"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val ADD_MEMORY_GEO = "add_memory_geo/{lat}/{lng}/{placeName}"
    const val CHAT_DETAIL = "chat_detail/{chatId}/{recipientId}"

    fun otherProfile(userId: String) = "other_profile/$userId"
    fun memoryDetail(memoryId: String) = "memory_detail/$memoryId"
    fun chatDetail(chatId: String, recipientId: String) = "chat_detail/$chatId/$recipientId"
    fun addMemoryGeo(lat: Double, lng: Double, placeName: String): String {
        val encodedPlace = URLEncoder.encode(placeName.ifBlank { "Location" }, StandardCharsets.UTF_8.toString())
        return "add_memory_geo/$lat/$lng/$encodedPlace"
    }
}

@Composable
fun GoMemoAppNavigation(
    navController: NavHostController = rememberNavController(),
    authRepository: com.example.data.repository.AuthRepository = remember { com.example.data.repository.AuthRepository() }
) {
    val isUserLoggedIn = authRepository.isLoggedIn
    val startDestination = if (isUserLoggedIn) Routes.MAIN else Routes.AUTH

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.AUTH) {
            val authViewModel: AuthViewModel = viewModel()
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
                onNavigateToDiagnostics = {
                    navController.navigate(Routes.DIAGNOSTICS)
                }
            )
        }

        composable(Routes.DIAGNOSTICS) {
            com.example.ui.screens.diagnostics.DiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MAIN) {
            MainContainerScreen(
                onNavigateToMemory = { id -> navController.navigate(Routes.memoryDetail(id)) },
                onNavigateToAuthor = { id ->
                    val currentUid = authRepository.currentUserId
                    if (id != currentUid) {
                        navController.navigate(Routes.otherProfile(id))
                    }
                },
                onNavigateToLocation = { lat, lng, place ->
                    navController.navigate(Routes.addMemoryGeo(lat, lng, place))
                },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onNavigateToChat = { chatId, recipientId ->
                    navController.navigate(Routes.chatDetail(chatId, recipientId))
                },
                onLogoutSuccess = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.EDIT_PROFILE) {
            val profileViewModel: ProfileViewModel = viewModel()
            EditProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                onLogoutSuccess = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.OTHER_PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val viewModel = remember(userId) { OtherProfileViewModel(userId) }
            OtherProfileScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onMemoryClick = { memId -> navController.navigate(Routes.memoryDetail(memId)) },
                onOpenChat = { chatId, recipientId ->
                    navController.navigate(Routes.chatDetail(chatId, recipientId))
                }
            )
        }

        composable(
            route = Routes.MEMORY_DETAIL,
            arguments = listOf(navArgument("memoryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val memoryId = backStackEntry.arguments?.getString("memoryId") ?: ""
            val viewModel = remember(memoryId) { MemoryDetailsViewModel(memoryId) }
            MemoryDetailsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onAuthorClick = { uid ->
                    val currentUid = authRepository.currentUserId
                    if (uid != currentUid) {
                        navController.navigate(Routes.otherProfile(uid))
                    }
                },
                onLocationClick = { lat, lng, place ->
                    navController.navigate(Routes.addMemoryGeo(lat, lng, place))
                }
            )
        }

        composable(Routes.SEARCH) {
            val searchViewModel: SearchViewModel = viewModel()
            SearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onMemoryClick = { memId -> navController.navigate(Routes.memoryDetail(memId)) },
                onUserClick = { uid ->
                    val currentUid = authRepository.currentUserId
                    if (uid != currentUid) {
                        navController.navigate(Routes.otherProfile(uid))
                    }
                }
            )
        }

        composable(Routes.NOTIFICATIONS) {
            val notificationsViewModel: NotificationsViewModel = viewModel()
            NotificationsScreen(
                viewModel = notificationsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onMemoryClick = { memId -> navController.navigate(Routes.memoryDetail(memId)) },
                onUserClick = { uid ->
                    val currentUid = authRepository.currentUserId
                    if (uid != currentUid) {
                        navController.navigate(Routes.otherProfile(uid))
                    }
                }
            )
        }

        composable(
            route = Routes.CHAT_DETAIL,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("recipientId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val recipientId = backStackEntry.arguments?.getString("recipientId") ?: ""
            val viewModel = remember(chatId, recipientId) { ChatDetailViewModel(chatId, recipientId) }
            ChatDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onUserProfileClick = { uid -> navController.navigate(Routes.otherProfile(uid)) }
            )
        }

        composable(
            route = Routes.ADD_MEMORY_GEO,
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType },
                navArgument("lng") { type = NavType.StringType },
                navArgument("placeName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val lat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
            val lng = backStackEntry.arguments?.getString("lng")?.toDoubleOrNull() ?: 0.0
            val placeNameRaw = backStackEntry.arguments?.getString("placeName") ?: ""
            val placeName = URLDecoder.decode(placeNameRaw, StandardCharsets.UTF_8.toString())

            val addViewModel: AddMemoryViewModel = viewModel()
            remember(lat, lng, placeName) {
                addViewModel.setInitialLocation(lat, lng, placeName)
            }

            AddMemoryScreen(
                viewModel = addViewModel,
                onNavigateBack = { navController.popBackStack() },
                onMemorySaved = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainContainerScreen(
    onNavigateToMemory: (String) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToLocation: (Double, Double, String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToChat: (chatId: String, recipientId: String) -> Unit,
    onLogoutSuccess: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.HOME) }

    val homeViewModel: HomeViewModel = viewModel()
    val discoverViewModel: DiscoverViewModel = viewModel()
    val mapViewModel: MapViewModel = viewModel()
    val addViewModel: AddMemoryViewModel = viewModel()
    val messagesViewModel: MessagesViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()

    Scaffold(
        bottomBar = {
            GoMemoBottomBar(
                currentTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.HOME -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onMemoryClick = onNavigateToMemory,
                        onAuthorClick = onNavigateToAuthor,
                        onLocationClick = { lat, lng, place ->
                            selectedTab = NavigationTab.DISCOVER
                            mapViewModel.centerOnCoordinates(lat, lng, place)
                        },
                        onAddMemoryClick = { selectedTab = NavigationTab.ADD },
                        onSearchClick = onNavigateToSearch,
                        onNotificationsClick = onNavigateToNotifications
                    )
                }
                NavigationTab.DISCOVER -> {
                    DiscoverScreen(
                        viewModel = discoverViewModel,
                        mapViewModel = mapViewModel,
                        onMemoryClick = onNavigateToMemory,
                        onUserClick = onNavigateToAuthor,
                        onAddMemoryWithLocation = onNavigateToLocation
                    )
                }
                NavigationTab.ADD -> {
                    AddMemoryScreen(
                        viewModel = addViewModel,
                        onNavigateBack = { selectedTab = NavigationTab.HOME },
                        onMemorySaved = {
                            selectedTab = NavigationTab.HOME
                            profileViewModel.loadProfile()
                        }
                    )
                }
                NavigationTab.MESSAGES -> {
                    MessagesScreen(
                        viewModel = messagesViewModel,
                        onOpenChat = { chatId, recipientId ->
                            onNavigateToChat(chatId, recipientId)
                        }
                    )
                }
                NavigationTab.PROFILE -> {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onEditProfileClick = onNavigateToEditProfile,
                        onSettingsClick = onNavigateToEditProfile,
                        onMemoryClick = onNavigateToMemory,
                        onAddMemoryClick = { selectedTab = NavigationTab.ADD }
                    )
                }
            }
        }
    }
}
