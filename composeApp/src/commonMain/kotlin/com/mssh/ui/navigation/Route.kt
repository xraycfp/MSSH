package com.mssh.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation routes for the app.
 */
sealed interface Route {
    @Serializable
    data object HostList : Route

    @Serializable
    data class HostEdit(val hostId: Long = -1) : Route

    @Serializable
    data class Terminal(val hostId: Long) : Route

    @Serializable
    data object KeyManager : Route

    @Serializable
    data object Settings : Route
}
