package com.adulthub.tv.ui.navigation

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.adulthub.tv.ui.auth.*
import com.adulthub.tv.ui.detail.ItemDetailViewModel
import com.adulthub.tv.ui.home.HomeViewModel
import com.adulthub.tv.ui.library.LibraryViewModel
import com.adulthub.tv.ui.person.PersonDetailViewModel
import com.adulthub.tv.ui.player.PlayerScreen
import com.adulthub.tv.ui.search.SearchViewModel
import com.adulthub.tv.ui.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

internal fun mobileTopLevelNavigationRoute(routePattern: String): String = when (routePattern) {
    Screen.Library.route -> Screen.Library.createRoute()
    Screen.Search.route -> Screen.Search.createRoute()
    else -> routePattern
}

@Composable
fun MobileAppNavigation(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val auth: AuthViewModel = koinViewModel()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(auth) {
        auth.navEvents.collect { event ->
            val destination = when (event) {
                is AuthNavEvent.NavigateToLogin -> Screen.Login.route
                AuthNavEvent.NavigateToQuickConnect -> Screen.QuickConnect.route
                AuthNavEvent.NavigateToLibrarySelect -> Screen.LibrarySelect.route
                AuthNavEvent.NavigateToHome -> Screen.Home.route
                AuthNavEvent.NavigateToServer -> Screen.Server.route
            }
            nav.navigate(destination) {
                popUpTo(nav.graph.id) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    val topLevelRoutes = setOf(Screen.Home.route, Screen.Library.route, Screen.Search.route, Screen.Settings.route)
    val showBar = route in topLevelRoutes
    var lastHomeBackAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(route) { lastHomeBackAt = 0L }
    BackHandler(enabled = route == Screen.Home.route) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHomeBackAt < 2_000L) (context as? Activity)?.finish()
        else {
            lastHomeBackAt = now
            Toast.makeText(context, "Press Back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBar) NavigationBar {
                listOf(
                    Screen.Home.route to ("Home" to Icons.Filled.Home),
                    Screen.Library.route to ("Library" to Icons.AutoMirrored.Filled.MenuBook),
                    Screen.Search.route to ("Search" to Icons.Filled.Search),
                    Screen.Settings.route to ("Settings" to Icons.Filled.Settings),
                ).forEach { (destination, pair) ->
                    val selected = route == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(mobileTopLevelNavigationRoute(destination)) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(pair.second, contentDescription = pair.first) },
                        label = { Text(pair.first) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Screen.Splash.route, modifier = Modifier.padding(padding)) {
            composable(Screen.Splash.route) { MobilePage("Adult Hub", "Connecting to your library…") }
            composable(Screen.Server.route) { MobileServerScreen(auth) }
            composable(Screen.Login.route) { MobileLoginScreen(auth) { nav.popBackStack() } }
            composable(Screen.QuickConnect.route) { MobileQuickConnectScreen(auth) { nav.popBackStack() } }
            composable(Screen.LibrarySelect.route) { MobileLibrarySelectScreen(auth) }
            composable(Screen.Home.route) { homeEntry ->
                val vm: HomeViewModel = koinViewModel()
                MobileHomeScreen(vm, isCurrentDestination = entry?.id == homeEntry.id, onItem = { nav.navigate(Screen.ItemDetail.createRoute(it)) }, onPlay = { nav.navigate(Screen.Player.createRoute(it)) }, onPerson = { id, name -> nav.navigate(Screen.PersonDetail.createRoute(id, name)) }, onSearch = { nav.navigate(Screen.Search.createRoute()) }, onLibrary = { nav.navigate(Screen.Library.createRoute()) }, onCategory = { nav.navigate(Screen.Library.createRoute(it)) })
            }
            composable(Screen.Library.route, arguments = listOf(navArgument("categoryId") { type = NavType.StringType; nullable = true; defaultValue = null })) { back ->
                val vm: LibraryViewModel = koinViewModel()
                MobileLibraryScreen(vm, back.arguments?.getString("categoryId"), onItem = { nav.navigate(Screen.ItemDetail.createRoute(it)) }, onPlay = { nav.navigate(Screen.Player.createRoute(it)) }, onPerson = { id, name -> nav.navigate(Screen.PersonDetail.createRoute(id, name)) })
            }
            composable(Screen.Search.route, arguments = listOf(navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = null })) { back ->
                val vm: SearchViewModel = koinViewModel()
                MobileSearchScreen(vm, back.arguments?.getString("query"), onItem = { nav.navigate(Screen.ItemDetail.createRoute(it)) }, onPlay = { nav.navigate(Screen.Player.createRoute(it)) }, onPerson = { id, name -> nav.navigate(Screen.PersonDetail.createRoute(id, name)) })
            }
            composable(Screen.Settings.route) {
                com.adulthub.tv.ui.settings.SettingsScreen(
                    onNavigateBack = { nav.popBackStack() },
                    onSignOut = { nav.navigate(Screen.Server.route) { popUpTo(0) } },
                    isMobile = true,
                )
            }
            composable(Screen.ItemDetail.route, arguments = listOf(navArgument("itemId") { type = NavType.StringType })) { back ->
                val vm: ItemDetailViewModel = koinViewModel()
                MobileItemDetailScreen(
                    vm = vm,
                    itemId = back.arguments?.getString("itemId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onPlay = { id, resume -> nav.navigate(Screen.Player.createRoute(id, resume)) },
                    onItem = { nav.navigate(Screen.ItemDetail.createRoute(it)) },
                    onPerson = { id, name -> nav.navigate(Screen.PersonDetail.createRoute(id, name)) },
                    onSearch = { nav.navigate(Screen.Search.createRoute(it)) },
                )
            }
            composable(Screen.PersonDetail.route, arguments = listOf(navArgument("personId") { type = NavType.StringType }, navArgument("personName") { type = NavType.StringType })) { back ->
                val vm: PersonDetailViewModel = koinViewModel()
                MobilePersonScreen(vm, back.arguments?.getString("personId").orEmpty(), back.arguments?.getString("personName").orEmpty(), onItem = { nav.navigate(Screen.ItemDetail.createRoute(it)) })
            }
            composable(Screen.Player.route, arguments = listOf(navArgument("itemId") { type = NavType.StringType }, navArgument("resume") { type = NavType.StringType; nullable = true; defaultValue = null })) { back ->
                PlayerScreen(back.arguments?.getString("itemId").orEmpty(), back.arguments?.getString("resume")?.toBooleanStrictOrNull(), { nav.popBackStack() }, mobileControls = true)
            }
        }
    }
}
