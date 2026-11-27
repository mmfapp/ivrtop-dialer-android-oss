package com.ivrtop.dialer.ui

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations (Navigation Compose 2.8+). Mirrored in docs/UI_UX_SPECIFICATION.md. */
@Serializable data object FavoritesRoute
@Serializable data object RecentsRoute
@Serializable data class DialerRoute(val number: String? = null)
@Serializable data object ContactsRoute
@Serializable data object SettingsRoute
@Serializable data object AccountsRoute
@Serializable data class AccountEditRoute(val accountId: String? = null)
/** [fromLink]: a setup link is waiting in [SetupLinkHolder] (never passed as an argument – it holds the password). */
@Serializable data class OnboardingRoute(val fromLink: Boolean = false, val skipWelcome: Boolean = false)
@Serializable data object BlockingRoute
@Serializable data object DndRoute
@Serializable data object VoicemailRoute
@Serializable data object DiagnosticsRoute
