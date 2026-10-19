package com.adulthub.tv.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Server : Screen("server")
    data object Login : Screen("login")
    data object QuickConnect : Screen("quick_connect")
    data object LibrarySelect : Screen("library_select")
    data object Home : Screen("home")
    data object Library : Screen("library?categoryId={categoryId}") {
        fun createRoute(categoryId: String? = null): String =
            if (categoryId.isNullOrBlank()) "library"
            else "library?categoryId=${android.net.Uri.encode(categoryId)}"
    }
    data object ItemDetail : Screen("item_detail/{itemId}") {
        fun createRoute(itemId: String): String = "item_detail/${android.net.Uri.encode(itemId)}"
    }
    data object PersonDetail : Screen("person_detail/{personId}/{personName}") {
        fun createRoute(personId: String, personName: String): String =
            "person_detail/${android.net.Uri.encode(personId)}/${android.net.Uri.encode(personName)}"
    }
    data object Search : Screen("search?query={query}") {
        fun createRoute(query: String = ""): String =
            if (query.isNotBlank()) "search?query=${android.net.Uri.encode(query)}" else "search"
    }
    data object Settings : Screen("settings")
    data object Player : Screen("player/{itemId}?resume={resume}") {
        fun createRoute(itemId: String, resume: Boolean? = null): String {
            val encodedItemId = android.net.Uri.encode(itemId)
            val route = "player/$encodedItemId"
            return if (resume == null) route else "$route?resume=$resume"
        }
    }
}
