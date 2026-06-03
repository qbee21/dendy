package com.dendy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dendy.app.di.AppGraph
import com.dendy.core.model.RomId
import com.dendy.feature.library.LIBRARY_ROUTE
import com.dendy.feature.library.LibraryRoute
import com.dendy.feature.player.PLAYER_ROUTE_PATTERN
import com.dendy.feature.player.PlayerRoute
import com.dendy.feature.player.playerRoute
import com.dendy.feature.settings.SETTINGS_ROUTE
import com.dendy.feature.settings.SettingsRoute

@Composable
fun DendyApp(
    appGraph: AppGraph,
) {
    val navController = rememberNavController()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = LIBRARY_ROUTE,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(LIBRARY_ROUTE) {
                LibraryRoute(
                    dependencies = appGraph.libraryDependencies,
                    onOpenPlayer = { navController.navigate(playerRoute(it)) },
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            composable(
                route = PLAYER_ROUTE_PATTERN,
                arguments = listOf(navArgument("romId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val romId = RomId(backStackEntry.arguments?.getString("romId").orEmpty())
                PlayerRoute(
                    romId = romId,
                    dependencies = appGraph.playerDependencies,
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            composable(SETTINGS_ROUTE) {
                SettingsRoute(
                    dependencies = appGraph.settingsDependencies,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
