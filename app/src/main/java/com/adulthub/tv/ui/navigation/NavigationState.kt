package com.adulthub.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Reads the current back stack entry in the scope of the destination that asks for it.
 *
 * Reading the back stack state in the scope of the whole navigation graph would recompose
 * every destination on each back stack change. Each destination therefore asks for its own
 * state, so only the affected destination is recomposed.
 */
@Composable
internal fun rememberIsCurrentDestination(
    navController: NavHostController,
    entry: NavBackStackEntry,
): Boolean {
    val currentEntry by navController.currentBackStackEntryAsState()
    return currentEntry?.id == entry.id
}
