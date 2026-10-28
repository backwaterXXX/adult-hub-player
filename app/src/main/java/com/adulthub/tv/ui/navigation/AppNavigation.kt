package com.adulthub.tv.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.ui.auth.AuthNavEvent
import com.adulthub.tv.ui.auth.AuthViewModel
import com.adulthub.tv.ui.auth.LibrarySelectScreen
import com.adulthub.tv.ui.auth.LoginScreen
import com.adulthub.tv.ui.auth.QuickConnectScreen
import com.adulthub.tv.ui.auth.ServerScreen
import com.adulthub.tv.ui.auth.SplashScreen
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.home.HomeScreen
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * How long the app stays armed for a second Back press after the exit prompt appears.
 *
 * A TV remote is pressed slowly, and the prompt itself is easy to miss; two seconds is
 * short enough that a user who reads the prompt before pressing again misses the window.
 */
internal const val EXIT_CONFIRM_WINDOW_MS = 3_000L

internal fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.navigateToItemDetail(
    itemId: String,
    returnToPreviousDetail: Boolean = false,
) {
    val route = Screen.ItemDetail.createRoute(itemId)
    if (
        returnToPreviousDetail &&
        previousBackStackEntry?.destination?.route == Screen.ItemDetail.route
    ) {
        popBackStack()
        navigateSingleTop(route)
    } else {
        navigate(route) {
            launchSingleTop = true
        }
    }
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = koinViewModel(),
) {
    LaunchedEffect(authViewModel) {
        authViewModel.navEvents.collect { event ->
            when (event) {
                is AuthNavEvent.NavigateToServer -> {
                    navController.navigate(Screen.Server.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is AuthNavEvent.NavigateToLogin -> {
                    navController.navigate(Screen.Login.route) {
                        launchSingleTop = true
                    }
                }
                is AuthNavEvent.NavigateToQuickConnect -> {
                    navController.navigate(Screen.QuickConnect.route) {
                        launchSingleTop = true
                    }
                }
                is AuthNavEvent.NavigateToLibrarySelect -> {
                    navController.navigate(Screen.LibrarySelect.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is AuthNavEvent.NavigateToHome -> {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.fillMaxSize(),
        ) {
        composable(Screen.Splash.route) {
            SplashScreen()
        }
        composable(Screen.Server.route) {
            ServerScreen(viewModel = authViewModel)
        }
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onBackToServer = { navController.popBackStack() },
            )
        }
        composable(Screen.QuickConnect.route) {
            QuickConnectScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.LibrarySelect.route) {
            LibrarySelectScreen(viewModel = authViewModel)
        }
        composable(Screen.Home.route) { backStackEntry ->
            HomeScreen(
                isCurrentDestination = rememberIsCurrentDestination(navController, backStackEntry),
                onNavigateToItemDetail = { itemId ->
                    navController.navigateToItemDetail(itemId)
                },
                onNavigateToPersonDetail = { personId, personName ->
                    navController.navigateSingleTop(Screen.PersonDetail.createRoute(personId, personName))
                },
                onNavigateToPlayer = { playItemId ->
                    navController.navigateSingleTop(Screen.Player.createRoute(playItemId))
                },
                onNavigateToLibrary = {
                    navController.navigateSingleTop(Screen.Library.createRoute())
                },
                onNavigateToSearch = {
                    navController.navigateSingleTop(Screen.Search.createRoute())
                },
                onNavigateToSettings = {
                    navController.navigateSingleTop(Screen.Settings.route)
                },
                onNavigateToLibraryCategory = { categoryId ->
                    navController.navigateSingleTop(Screen.Library.createRoute(categoryId))
                },
            )
        }
        composable(
            route = Screen.ItemDetail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId").orEmpty()
            com.adulthub.tv.ui.detail.ItemDetailScreen(
                itemId = itemId,
                isCurrentDestination = rememberIsCurrentDestination(navController, backStackEntry),
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { playItemId, resume ->
                    navController.navigateSingleTop(Screen.Player.createRoute(playItemId, resume))
                },
                onNavigateToItemDetail = { nextItemId ->
                    navController.navigateToItemDetail(nextItemId)
                },
                onNavigateToPersonDetail = { personId, personName ->
                    navController.navigateSingleTop(Screen.PersonDetail.createRoute(personId, personName))
                },
                onNavigateToSearch = { query ->
                    navController.navigateSingleTop(Screen.Search.createRoute(query))
                },
            )
        }
        composable(
            route = Screen.PersonDetail.route,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType },
                navArgument("personName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId").orEmpty()
            val personName = backStackEntry.arguments?.getString("personName").orEmpty()
            com.adulthub.tv.ui.person.PersonDetailScreen(
                personId = personId,
                personName = personName,
                isCurrentDestination = rememberIsCurrentDestination(navController, backStackEntry),
                onNavigateBack = { navController.popBackStack() },
                onNavigateToItemDetail = { nextItemId ->
                    navController.navigateToItemDetail(nextItemId, returnToPreviousDetail = true)
                },
            )
        }
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("resume") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId").orEmpty()
            val resume = backStackEntry.arguments?.getString("resume")?.toBooleanStrictOrNull()
            com.adulthub.tv.ui.player.PlayerScreen(
                itemId = itemId,
                resume = resume,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Screen.Library.route,
            arguments = listOf(
                navArgument("categoryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            com.adulthub.tv.ui.library.LibraryScreen(
                initialCategoryId = backStackEntry.arguments?.getString("categoryId"),
                isCurrentDestination = rememberIsCurrentDestination(navController, backStackEntry),
                onNavigateBack = { navController.popBackStack() },
                onNavigateToItemDetail = { itemId ->
                    navController.navigateToItemDetail(itemId)
                },
                onNavigateToPersonDetail = { personId, personName ->
                    navController.navigateSingleTop(Screen.PersonDetail.createRoute(personId, personName))
                },
                onNavigateToPlayer = { playItemId ->
                    navController.navigateSingleTop(Screen.Player.createRoute(playItemId))
                },
            )
        }
        composable(
            route = Screen.Search.route,
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query")
            com.adulthub.tv.ui.search.SearchScreen(
                initialQuery = query,
                isCurrentDestination = rememberIsCurrentDestination(navController, backStackEntry),
                onNavigateBack = { navController.popBackStack() },
                onNavigateToItemDetail = { itemId ->
                    navController.navigateToItemDetail(itemId)
                },
                onNavigateToPersonDetail = { personId, personName ->
                    navController.navigateSingleTop(Screen.PersonDetail.createRoute(personId, personName))
                },
                onNavigateToPlayer = { itemId ->
                    navController.navigateSingleTop(Screen.Player.createRoute(itemId))
                },
            )
        }
        composable(Screen.Settings.route) {
            com.adulthub.tv.ui.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignOut = {
                    navController.navigate(Screen.Server.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }

        TvExitPrompt(
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Handles the back gesture and the "press back again to exit" prompt.
 *
 * The current route is read inside this composable so that back stack changes do not
 * recompose the whole navigation graph.
 */
@Composable
private fun TvExitPrompt(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val isFirstScreen = currentRoute == Screen.Home.route || currentRoute == Screen.Server.route
    val context = LocalContext.current
    val exitMessage = stringResource(R.string.press_back_again_to_exit)
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var showExitPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        showExitPrompt = false
        lastBackPressTime = 0L
    }

    LaunchedEffect(showExitPrompt) {
        if (showExitPrompt) {
            delay(EXIT_CONFIRM_WINDOW_MS)
            showExitPrompt = false
        }
    }

    BackHandler(enabled = true) {
        if (isFirstScreen) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBackPressTime < EXIT_CONFIRM_WINDOW_MS) {
                context.findActivity()?.finish()
            } else {
                lastBackPressTime = now
                showExitPrompt = true
                Toast.makeText(
                    context,
                    exitMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            if (!navController.popBackStack()) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showExitPrompt,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(bottom = 36.dp),
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = Color(0xF0181818),
                    shape = RoundedCornerShape(24.dp),
                )
                .border(
                    width = 1.dp,
                    color = NeonPink,
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.press_back_again_to_exit),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}
