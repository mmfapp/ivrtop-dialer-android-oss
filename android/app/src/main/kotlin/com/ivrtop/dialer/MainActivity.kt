package com.ivrtop.dialer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.datastore.ThemeMode
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.BrandLogoChip
import com.ivrtop.dialer.core.designsystem.theme.IvrtopTheme
import com.ivrtop.dialer.core.security.BiometricGate
import com.ivrtop.dialer.feature.activecall.CallActivity
import com.ivrtop.dialer.ui.IvrtopApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Navigation requests derived from external intents, consumed once by the UI. */
sealed interface ExternalRequest {
    data class Setup(val link: String) : ExternalRequest
    data class Dial(val number: String) : ExternalRequest
    data class Destination(val dest: String) : ExternalRequest
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()
    private val external = mutableStateOf<ExternalRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !vm.ui.value.ready }
        if (savedInstanceState == null) handleIntent(intent)

        // No Play Install Referrer in the open-source build: after installing from the landing
        // page the user taps the link again or types the one-time code shown there.

        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val locked by vm.locked.collectAsStateWithLifecycle()
            val dark = when (ui.settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            IvrtopTheme(darkTheme = dark, dynamicColor = ui.settings.dynamicColor, reduceMotion = ui.settings.reduceMotion) {
                if (!ui.ready) return@IvrtopTheme
                if (ui.settings.appLock && locked) {
                    LockScreen(onUnlock = ::authenticate)
                    LaunchedEffect(Unit) { authenticate() }
                } else {
                    IvrtopApp(
                        ui = ui,
                        external = external.value,
                        onExternalConsumed = { external.value = null },
                        onSwitchAccount = vm::switchAccount,
                        onCallStarted = { id -> startActivity(CallActivity.intent(this, id)) },
                        onAddContact = ::addContact,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }

    override fun onStart() {
        super.onStart()
        if (vm.shouldRelock()) vm.locked.value = true
    }

    private fun authenticate() {
        lifecycleScope.launch {
            if (!BiometricGate.isAvailable(this@MainActivity) ||
                BiometricGate.authenticate(this@MainActivity, getString(R.string.unlock_title), getString(R.string.unlock_subtitle))
            ) vm.unlock()
        }
    }

    /**
     * Intent validation: only our verified host + setup path is accepted as a setup link
     * (ProvisioningRepository re-validates every parameter and the allowed SIP domain). tel:/sip: only PRE-FILL the keypad –
     * an external app can never make us place a call without the user pressing "call".
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data: Uri? = intent.data
        when {
            intent.action == Intent.ACTION_VIEW && data?.scheme == "https" &&
                data.host.equals(BuildConfig.APP_LINK_HOST, ignoreCase = true) &&
                data.path?.trimEnd('/') == BuildConfig.APP_LINK_PATH.trimEnd('/') ->
                // Contains the SIP password – kept in memory only, validated by ProvisioningRepository.
                external.value = ExternalRequest.Setup(data.toString())
            (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) && (data?.scheme == "tel" || data?.scheme == "sip") -> {
                val number = data.schemeSpecificPart.orEmpty()
                if (number.isNotBlank()) external.value = ExternalRequest.Dial(PhoneNumbers.stripFormatting(number).take(128))
            }
            intent.hasExtra(EXTRA_DEST) -> external.value = ExternalRequest.Destination(intent.getStringExtra(EXTRA_DEST)!!)
        }
        // Drop the data so the token isn't re-processed on recreation and never lingers in the intent.
        setIntent(Intent(this, MainActivity::class.java))
    }

    private fun addContact(number: String) {
        val i = Intent(Intent.ACTION_INSERT_OR_EDIT)
            .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
        runCatching { startActivity(i) }
    }

    companion object {
        const val EXTRA_DEST = "dest"
        const val DEST_RECENTS = "recents"
        const val DEST_VOICEMAIL = "voicemail"
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            BrandLogoChip(height = 56.dp)
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { scope.launch { onUnlock() } }) { Text(stringResource(R.string.unlock_btn)) }
        }
    }
}
