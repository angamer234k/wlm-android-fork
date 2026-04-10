package com.yourapp.wlm.presentation.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ContactList : Screen("contact_list")
    object Chat : Screen("chat/{contactEmail}") {
        fun createRoute(contactEmail: String) = "chat/$contactEmail"
    }
    object Profile : Screen("profile")
    object Settings : Screen("settings")
}
