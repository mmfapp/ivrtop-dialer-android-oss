package com.ivrtop.dialer.telephony.sipengine

import com.ivrtop.dialer.telephony.sip.SipEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The single place where the concrete engine is chosen. Swap here to change SIP stacks. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SipEngineModule {
    @Binds abstract fun sipEngine(impl: PjsipEngine): SipEngine
}
