package com.ivrtop.dialer.feature.activecall

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivrtop.dialer.core.datastore.ThemeMode
import com.ivrtop.dialer.core.designsystem.component.errorMessageRes
import com.ivrtop.dialer.core.designsystem.theme.IvrtopTheme
import com.ivrtop.dialer.feature.incoming.IncomingCallScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/**
 * Incoming + in-call UI. Launched from the full-screen intent, the CallStyle notification,
 * or after placing a call. Finishes itself shortly after the last call ended.
 */
@AndroidEntryPoint
class CallActivity : AppCompatActivity() {

    private val vm: CallViewModel by viewModels()
    private var keyTone: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handleIntent(intent)

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = when (state.settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            LaunchedEffect(Unit) {
                vm.errors.collect { Toast.makeText(this@CallActivity, errorMessageRes(it), Toast.LENGTH_LONG).show() }
            }
            LaunchedEffect(state.calls.isEmpty()) {
                if (state.calls.isEmpty()) {
                    delay(400)
                    if (vm.state.value.calls.isEmpty()) finishAndRemoveTask()
                }
            }
            IvrtopTheme(darkTheme = dark, dynamicColor = state.settings.dynamicColor, reduceMotion = state.settings.reduceMotion) {
                val call = state.focused ?: return@IvrtopTheme
                if (call.phase.isRinging && state.live.none { it.phase.isConnected }) {
                    IncomingCallScreen(
                        call = call,
                        accountLabel = state.accounts[call.accountId]?.label,
                        photoUri = state.photoFor(call),
                        onAnswer = { vm.answer(call.callId) },
                        onDecline = { vm.decline(call.callId) },
                        onSilence = { vm.silence(call.callId) },
                    )
                } else {
                    // A second incoming call while talking is shown as a banner-style incoming screen too.
                    val waiting = state.live.firstOrNull { it.phase.isRinging }
                    if (waiting != null) {
                        IncomingCallScreen(
                            call = waiting,
                            accountLabel = state.accounts[waiting.accountId]?.label,
                            photoUri = state.photoFor(waiting),
                            onAnswer = { vm.answer(waiting.callId) },
                            onDecline = { vm.decline(waiting.callId) },
                            onSilence = { vm.silence(waiting.callId) },
                        )
                    } else {
                        ActiveCallScreen(state = state, call = call, vm = vm, onKeyTone = ::playKey)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID) ?: return
        vm.focus(callId)
        if (intent.getBooleanExtra(EXTRA_ANSWER, false)) {
            vm.answer(callId)
            intent.removeExtra(EXTRA_ANSWER)
        }
    }

    private fun playKey(c: Char) {
        val tone = when (c) {
            in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (c - '0')
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        runCatching {
            (keyTone ?: ToneGenerator(AudioManager.STREAM_VOICE_CALL, 50).also { keyTone = it }).startTone(tone, 120)
        }
    }

    override fun onDestroy() {
        keyTone?.release()
        keyTone = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_ANSWER = "answer"

        fun intent(context: Context, callId: String, answer: Boolean = false): Intent =
            Intent(context, CallActivity::class.java)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_ANSWER, answer)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
