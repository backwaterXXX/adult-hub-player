package com.adulthub.tv.ui.navigation

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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.adulthub.tv.R
import com.adulthub.tv.ui.auth.*
import com.adulthub.tv.ui.detail.ItemDetailViewModel
import com.adulthub.tv.ui.home.HomeViewModel
import com.adulthub.tv.ui.library.LibraryViewModel
import com.adulthub.tv.ui.person.PersonDetailViewModel
import com.adulthub.tv.ui.player.PlayerScreen
import com.adulthub.tv.ui.search.SearchViewModel
import com.adulthub.tv.ui.settings.SettingsViewModel
import com.adulthub.tv.ui.theme.AdultHubTvTheme
import org.koin.androidx.compose.koinViewModel

internal fun mobileTopLevelNavigationRoute(routePattern: String): String = when (routePattern) {
    Screen.Library.route -> Screen.Library.createRoute()
    Screen.Search.route -> Screen.Search.createRoute()
    else -> routePattern
}

private val MOBILE_TOP_LEVEL_ROUTES = setOf(
    Screen.Home.route,
    Screen.Library.route,
    Screen.Search.route,
    Screen.Settings.route,
)

/**
 * Every mobile navigation target is opened with `launchSingleTop`, so a double tap cannot
 * create two copies of the same screen with two view models and two requests.
 */
private fun NavHostController.navigateMobile(route: String) {
    navigate(route) { launchSingleTop = true }
}

@Composable
fun MobileAppNavigation(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val auth: AuthViewModel = koinViewModel()

    LaunchedEffect(auth) {
        auth.navEvents.collect { event ->
            val destination = when (event) {
                is AuthNavEvent.NavigateToLogin -> Screen.Login.route
                AuthNavEvent.NavigateToQuickConnect -> Screen.QuickConnect.route
                AuthNavEvent.NavigateToLibrarySelect -> Screen.LibrarySelect.route
                AuthNavEvent.NavigateToHome -> Screen.Home.route
                AuthNavEvent.NavigateToServer -> Screen.Server.route
            }
            // Login and quick connect are pushed on top of the server screen, so Back
            // returns there instead of leaving the app. Every other event moves the
            // session to a new root and has to drop the previous screens.
            val keepBackStack = event is AuthNavEvent.NavigateToLogin ||
                event is AuthNavEvent.NavigateToQuickConnect
            nav.navigate(destination) {
                if (!keepBackStack) {
                    popUpTo(nav.graph.id) { inclusive = false }
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            MobileBottomBar(navController = nav)
        },
    ) { padding ->
        MobileExitHandler(navController = nav)
        NavHost(navController = nav, startDestination = Screen.Splash.route, modifier = Modifier.padding(padding)) {
            composable(Screen.Splash.route) {
                MobilePage(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.mobile_splash_subtitle),
                )
            }
            composable(Screen.Server.route) { MobileServerScreen(auth) }
            composable(Screen.Login.route) { MobileLoginScreen(auth) { nav.popBackStack() } }
            composable(Screen.QuickConnect.route) { MobileQuickConnectScreen(auth) { nav.popBackStack() } }
            composable(Screen.LibrarySelect.route) { MobileLibrarySelectScreen(auth) }
            composable(Screen.Home.route) { homeEntry ->
                val vm: HomeViewModel = koinViewModel()
                MobileHomeScreen(vm, isCurrentDestination = rememberIsCurrentDestination(nav, homeEntry), onItem = { nav.navigateMobile(Screen.ItemDetail.createRoute(it)) }, onPlay = { nav.navigateMobile(Screen.Player.createRoute(it)) }, onPerson = { id, name -> nav.navigateMobile(Screen.PersonDetail.createRoute(id, name)) }, onSearch = { nav.navigateMobile(Screen.Search.createRoute()) }, onLibrary = { nav.navigateMobile(Screen.Library.createRoute()) }, onCategory = { nav.navigateMobile(Screen.Library.createRoute(it)) })
            }
            composable(Screen.Library.route, arguments = listOf(navArgument("categoryId") { type = NavType.StringType; nullable = true; defaultValue = null })) { back ->
                val vm: LibraryViewModel = koinViewModel()
                MobileLibraryScreen(vm, back.arguments?.getString("categoryId"), onItem = { nav.navigateMobile(Screen.ItemDetail.createRoute(it)) }, onPlay = { nav.navigateMobile(Screen.Player.createRoute(it)) }, onPerson = { id, name -> nav.navigateMobile(Screen.PersonDetail.createRoute(id, name)) })
            }
            composable(Screen.Search.route, arguments = listOf(navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = null })) { back ->
                val vm: SearchViewModel = koinViewModel()
                MobileSearchScreen(vm, back.arguments?.getString("query"), onItem = { nav.navigateMobile(Screen.ItemDetail.createRoute(it)) }, onPlay = { nav.navigateMobile(Screen.Player.createRoute(it)) }, onPerson = { id, name -> nav.navigateMobile(Screen.PersonDetail.createRoute(id, name)) })
            }
            composable(Screen.Settings.route) {
                // The settings screen is shared with the TV build and uses the TV material
                // components, so it is wrapped in the TV theme explicitly.
                AdultHubTvTheme {
                    com.adulthub.tv.ui.settings.SettingsScreen(
                        onNavigateBack = { nav.popBackStack() },
                        onSignOut = { nav.navigate(Screen.Server.route) { popUpTo(0) } },
                        isMobile = true,
                    )
                }
            }
            composable(Screen.ItemDetail.route, arguments = listOf(navArgument("itemId") { type = NavType.StringType })) { back ->
                val vm: ItemDetailViewModel = koinViewModel()
                MobileItemDetailScreen(
                    vm = vm,
                    itemId = back.arguments?.getString("itemId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onPlay = { id, resume -> nav.navigateMobile(Screen.Player.createRoute(id, resume)) },
                    onItem = { nav.navigateMobile(Screen.ItemDetail.createRoute(it)) },
                    onPerson = { id, name -> nav.navigateMobile(Screen.PersonDetail.createRoute(id, name)) },
                    onSearch = { nav.navigateMobile(Screen.Search.createRoute(it)) },
                )
            }
            composable(Screen.PersonDetail.route, arguments = listOf(navArgument("personId") { type = NavType.StringType }, navArgument("personName") { type = NavType.StringType })) { back ->
                val vm: PersonDetailViewModel = koinViewModel()
                MobilePersonScreen(vm, back.arguments?.getString("personId").orEmpty(), back.arguments?.getString("personName").orEmpty(), onItem = { nav.navigateMobile(Screen.ItemDetail.createRoute(it)) })
            }
            composable(Screen.Player.route, arguments = listOf(navArgument("itemId") { type = NavType.StringType }, navArgument("resume") { type = NavType.StringType; nullable = true; defaultValue = null })) { back ->
                AdultHubTvTheme {
                    PlayerScreen(
                        itemId = back.arguments?.getString("itemId").orEmpty(),
                        onBack = { nav.popBackStack() },
                        resume = back.arguments?.getString("resume")?.toBooleanStrictOrNull(),
                        mobileControls = true,
                    )
                }
            }
        }
    }
}

/**
 * Double Back exits the app from the screens that start a session.
 *
 * The handler sits outside the bottom bar, so it also covers the server screen where
 * the bar is hidden. It is registered before the graph on purpose: a Back press on a
 * deeper screen stays with that screen and with the navigation graph.
 */
@Composable
private fun MobileExitHandler(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isExitScreen = route == Screen.Home.route || route == Screen.Server.route
    val context = LocalContext.current
    val exitMessage = stringResource(R.string.press_back_again_to_exit)
    var lastBackPressAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(route) { lastBackPressAt = 0L }

    BackHandler(enabled = isExitScreen) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressAt < EXIT_CONFIRM_WINDOW_MS) {
            context.findActivity()?.finish()
        } else {
            lastBackPressAt = now
            Toast.makeText(context, exitMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Bottom navigation bar of the mobile variant.
 *
 * The current route is read inside this composable, so navigating in the graph does not
 * recompose the whole graph but only the bar itself.
 */
@Composable
private fun MobileBottomBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in MOBILE_TOP_LEVEL_ROUTES

    if (!showBar) return

    NavigationBar {
        listOf(
            Screen.Home.route to (stringResource(R.string.nav_home) to Icons.Filled.Home),
            Screen.Library.route to (stringResource(R.string.nav_library) to Icons.AutoMirrored.Filled.MenuBook),
            Screen.Search.route to (stringResource(R.string.nav_search) to Icons.Filled.Search),
            Screen.Settings.route to (stringResource(R.string.nav_settings) to Icons.Filled.Settings),
        ).forEach { (destination, pair) ->
            val selected = route == destination
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(mobileTopLevelNavigationRoute(destination)) {
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
}
