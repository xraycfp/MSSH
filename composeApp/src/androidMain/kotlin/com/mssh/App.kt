package com.mssh

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mssh.ui.hosts.HostEditScreen
import com.mssh.ui.hosts.HostListScreen
import com.mssh.ui.keys.KeyManagerScreen
import com.mssh.ui.navigation.Route
import com.mssh.ui.settings.SettingsScreen
import com.mssh.ui.terminal.TerminalScreen
import com.mssh.ui.theme.MsshTheme

@Composable
fun App() {
    MsshTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Route.HostList
        ) {
            composable<Route.HostList> {
                HostListScreen(
                    onHostClick = { hostId ->
                        navController.navigate(Route.Terminal(hostId))
                    },
                    onAddClick = {
                        navController.navigate(Route.HostEdit())
                    },
                    onEditClick = { hostId ->
                        navController.navigate(Route.HostEdit(hostId))
                    },
                    onKeysClick = {
                        navController.navigate(Route.KeyManager)
                    },
                    onSettingsClick = {
                        navController.navigate(Route.Settings)
                    }
                )
            }

            composable<Route.HostEdit> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.HostEdit>()
                HostEditScreen(
                    hostId = route.hostId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<Route.Terminal> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.Terminal>()
                TerminalScreen(
                    hostId = route.hostId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<Route.KeyManager> {
                KeyManagerScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable<Route.Settings> {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
