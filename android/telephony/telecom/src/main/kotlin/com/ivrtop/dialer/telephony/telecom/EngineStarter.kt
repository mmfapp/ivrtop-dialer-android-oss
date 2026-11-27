package com.ivrtop.dialer.telephony.telecom

import com.ivrtop.dialer.core.common.BuildInfo
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.security.SystemCaBundle
import com.ivrtop.dialer.telephony.sip.EngineConfig
import com.ivrtop.dialer.telephony.sip.SipEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Lazily boots the native SIP stack exactly once (cold start from push must be fast). */
@Singleton
class EngineStarter @Inject constructor(
    private val engine: SipEngine,
    private val settings: SettingsStore,
    private val buildInfo: BuildInfo,
    private val caBundle: SystemCaBundle,
) {
    private val mutex = Mutex()

    suspend fun ensureStarted(): SipEngine = mutex.withLock {
        if (!engine.isStarted) {
            val s = settings.current()
            val verbose = s.verboseSipLogging || buildInfo.debug
            engine.start(
                EngineConfig(
                    userAgent = buildInfo.userAgent,
                    caBundlePath = runCatching { caBundle.ensureBundle().absolutePath }.getOrNull(),
                    logLevel = if (verbose) 4 else 1,
                    logSink = { line -> if (line.isNotBlank()) Timber.tag("PJSIP").d(line) },
                    echoCancellation = s.echoCancellation,
                    noiseSuppression = s.noiseSuppression,
                    autoGainControl = s.autoGainControl,
                ),
            )
        }
        engine
    }
}
