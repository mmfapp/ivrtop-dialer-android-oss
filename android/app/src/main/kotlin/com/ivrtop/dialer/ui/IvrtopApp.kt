package com.ivrtop.dialer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ivrtop.dialer.ExternalRequest
import com.ivrtop.dialer.MainActivity
import com.ivrtop.dialer.MainUi
import com.ivrtop.dialer.core.datastore.StartTab
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.feature.accounts.AccountEditScreen
import com.ivrtop.dialer.feature.accounts.AccountsScreen
import com.ivrtop.dialer.feature.blocking.BlockingScreen
import com.ivrtop.dialer.feature.contacts.ContactsScreen
import com.ivrtop.dialer.feature.dialer.DialerScreen
import com.ivrtop.dialer.feature.dnd.DndScreen
import com.ivrtop.dialer.feature.favorites.FavoritesScreen
import com.ivrtop.dialer.feature.onboarding.OnboardingScreen
import com.ivrtop.dialer.feature.recents.RecentsScreen
import com.ivrtop.dialer.feature.settings.DiagnosticsScreen
import com.ivrtop.dialer.feature.settings.SettingsScreen
import com.ivrtop.dialer.feature.voicemail.VoicemailScreen
import kotlin.reflect.KClass

private data class Tab(val route: Any, val cls: KClass<*>, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(FavoritesRoute, FavoritesRoute::class, R.string.tab_favorites, Icons.Rounded.Star),
    Tab(RecentsRoute, RecentsRoute::class, R.string.tab_recents, Icons.Rounded.History),
    Tab(DialerRoute(), DialerRoute::class, R.string.tab_dialer, Icons.Rounded.Dialpad),
    Tab(ContactsRoute, ContactsRoute::class, R.string.tab_contacts, Icons.Rounded.Contacts),
    Tab(SettingsRoute, SettingsRoute::class, R.string.tab_settings, Icons.Rounded.Settings),
)

@Composable
fun IvrtopApp(
    ui: MainUi,
    external: ExternalRequest?,
    onExternalConsumed: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onCallStarted: (String) -> Unit,
    onAddContact: (String) -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val needsOnboarding = ui.accounts.isEmpty() && !ui.settings.onboardingCompleted

    val start: Any = remember {
        when {
            needsOnboarding -> OnboardingRoute()
            else -> when (ui.settings.startTab) {
                StartTab.FAVORITES -> FavoritesRoute
                StartTab.RECENTS -> RecentsRoute
                StartTab.CONTACTS -> ContactsRoute
                StartTab.DIALER -> DialerRoute()
            }
        }
    }

    fun goTab(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    LaunchedEffect(external) {
        when (external) {
            is ExternalRequest.Setup -> {
                SetupLinkHolder.put(external.link)
                nav.navigate(OnboardingRoute(fromLink = true, skipWelcome = true))
            }
            is ExternalRequest.Dial -> goTab(DialerRoute(external.number))
            is ExternalRequest.Destination -> when (external.dest) {
                MainActivity.DEST_RECENTS -> goTab(RecentsRoute)
                MainActivity.DEST_VOICEMAIL -> nav.navigate(VoicemailRoute)
            }
            null -> return@LaunchedEffect
        }
        onExternalConsumed()
    }

    val currentTab = TABS.firstOrNull { t -> dest?.hasRoute(t.cls) == true }
    val showChrome = currentTab != null

    // Bottom bar on phones, navigation rail on tablets / unfolded foldables; hidden on sub-screens.
    val adaptiveType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    NavigationSuiteScaffold(
        layoutType = if (showChrome) adaptiveType else NavigationSuiteType.None,
        navigationSuiteItems = {
            TABS.forEach { t ->
                item(
                    selected = currentTab == t,
                    onClick = { goTab(t.route) },
                    icon = {
                        val badge = when (t.cls) {
                            RecentsRoute::class -> ui.unreadMissed
                            SettingsRoute::class -> ui.voicemail
                            else -> 0
                        }
                        BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) { Icon(t.icon, contentDescription = null) }
                    },
                    label = { Text(stringResource(t.label)) },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                when {
                    showChrome -> Column(Modifier.statusBarsPadding()) {
                        AppHeader(ui, onSwitchAccount = onSwitchAccount, onDndClick = { nav.navigate(DndRoute) })
                    }
                    dest?.hasRoute(OnboardingRoute::class) == true -> Unit
                    else -> TopAppBar(
                        title = { Text(stringResource(titleFor(dest))) },
                        navigationIcon = {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                            }
                        },
                    )
                }
            },
        ) { pad ->
            NavHost(nav, startDestination = start, modifier = Modifier.fillMaxSize().padding(pad)) {
                composable<FavoritesRoute> { FavoritesScreen(onCallStarted = onCallStarted) }
                composable<RecentsRoute> { RecentsScreen(onCallStarted = onCallStarted, onAddContact = onAddContact) }
                composable<DialerRoute> { e ->
                    DialerScreen(
                        onCallStarted = onCallStarted,
                        onSetupAccount = { nav.navigate(OnboardingRoute(skipWelcome = true)) },
                        onAddContact = onAddContact,
                        initialNumber = e.toRoute<DialerRoute>().number,
                    )
                }
                composable<ContactsRoute> { ContactsScreen(onCallStarted = onCallStarted) }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onAccounts = { nav.navigate(AccountsRoute) },
                        onBlocking = { nav.navigate(BlockingRoute) },
                        onDnd = { nav.navigate(DndRoute) },
                        onSpeedDial = { goTab(FavoritesRoute) },
                        onVoicemail = { nav.navigate(VoicemailRoute) },
                        onDiagnostics = { nav.navigate(DiagnosticsRoute) },
                    )
                }
                composable<AccountsRoute> {
                    AccountsScreen(
                        onAddViaSetup = { nav.navigate(OnboardingRoute(skipWelcome = true)) },
                        onAddManual = { nav.navigate(AccountEditRoute()) },
                        onEdit = { id -> nav.navigate(AccountEditRoute(id)) },
                    )
                }
                composable<AccountEditRoute> { AccountEditScreen(onDone = { nav.popBackStack() }) }
                composable<OnboardingRoute> { e ->
                    val r = e.toRoute<OnboardingRoute>()
                    val link = remember(e.id) { if (r.fromLink) SetupLinkHolder.take() else null }
                    Column(Modifier.safeDrawingPadding()) {
                        OnboardingScreen(
                            deepLink = link,
                            skipWelcome = r.skipWelcome,
                            onManualSetup = { nav.navigate(AccountEditRoute()) },
                            onFinished = {
                                nav.navigate(DialerRoute()) { popUpTo(0) { inclusive = true } }
                            },
                        )
                    }
                }
                composable<BlockingRoute> { BlockingScreen() }
                composable<DndRoute> { DndScreen() }
                composable<VoicemailRoute> { VoicemailScreen(onCallStarted = onCallStarted) }
                composable<DiagnosticsRoute> { DiagnosticsScreen() }
            }
        }
    }
}

private fun titleFor(dest: androidx.navigation.NavDestination?): Int = when {
    dest == null -> R.string.app_name_he
    dest.hasRoute(AccountsRoute::class) -> R.string.accounts_title
    dest.hasRoute(AccountEditRoute::class) -> R.string.add_manual
    dest.hasRoute(BlockingRoute::class) -> R.string.blocking_title
    dest.hasRoute(DndRoute::class) -> R.string.dnd_title
    dest.hasRoute(VoicemailRoute::class) -> R.string.voicemail_title
    dest.hasRoute(DiagnosticsRoute::class) -> R.string.set_diagnostics
    else -> R.string.app_name_he
}
