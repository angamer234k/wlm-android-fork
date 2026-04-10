package com.yourapp.wlm.presentation.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourapp.wlm.presentation.screen.chat.ChatScreen
import com.yourapp.wlm.presentation.screen.contactlist.ContactListScreen
import com.yourapp.wlm.presentation.screen.login.LoginScreen
import com.yourapp.wlm.presentation.screen.profile.ProfileScreen
import com.yourapp.wlm.presentation.screen.settings.SettingsScreen

@Composable
fun AppNavGraph(
    startDestination: String = Screen.Login.route
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.ContactList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.ContactList.route) {
            ContactListScreen(
                onNavigateToChat = { email ->
                    navController.navigate(Screen.Chat.createRoute(email))
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
            )
        }
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("contactEmail") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactEmail = backStackEntry.arguments?.getString("contactEmail") ?: ""
            ChatScreen(
                contactEmail = contactEmail,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
