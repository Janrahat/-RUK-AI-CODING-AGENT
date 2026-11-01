package com.ruk.ai.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ruk.ai.ui.components.RukBottomBar
import com.ruk.ai.ui.screens.ApiKeysScreen
import com.ruk.ai.ui.screens.AuthScreen
import com.ruk.ai.ui.screens.ChatScreen
import com.ruk.ai.ui.screens.CodeAssistantScreen
import com.ruk.ai.ui.screens.HistoryScreen
import com.ruk.ai.ui.screens.HomeScreen
import com.ruk.ai.ui.screens.ImageGeneratorScreen
import com.ruk.ai.ui.screens.PdfAssistantScreen
import com.ruk.ai.ui.screens.ProfileScreen
import com.ruk.ai.ui.screens.SettingsScreen
import com.ruk.ai.ui.screens.SplashScreen
import com.ruk.ai.ui.screens.ToolDetailScreen
import com.ruk.ai.ui.screens.ToolsScreen
import com.ruk.ai.ui.screens.VideoGeneratorScreen

@Composable
fun RukNavGraph(
    startDestination: String,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = BottomTab.forRoute(currentRoute) != null

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                RukBottomBar(
                    current = BottomTab.forRoute(currentRoute)!!,
                    onSelect = { tab ->
                        if (tab.route != currentRoute) {
                            navController.navigate(tab.route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }
            composable(Routes.AUTH) {
                AuthScreen(
                    onAuthenticated = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(navController)
            }
            composable(Routes.CHAT) {
                ChatScreen(navController)
            }
            composable(Routes.TOOLS) {
                ToolsScreen(navController)
            }
            composable(Routes.HISTORY) {
                HistoryScreen(navController)
            }
            composable(Routes.PROFILE) {
                ProfileScreen(navController)
            }
            composable(Routes.IMAGE_GEN) {
                ImageGeneratorScreen(navController)
            }
            composable(Routes.VIDEO_GEN) {
                VideoGeneratorScreen(navController)
            }
            composable(Routes.CODE) {
                CodeAssistantScreen(navController)
            }
            composable(Routes.PDF) {
                PdfAssistantScreen(navController)
            }
            composable(Routes.API_KEYS) {
                ApiKeysScreen(navController)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(navController)
            }
            composable(Routes.TOOL_DETAIL) { entry ->
                val toolId = entry.arguments?.getString("toolId").orEmpty()
                ToolDetailScreen(navController, toolId)
            }
        }
    }
}
