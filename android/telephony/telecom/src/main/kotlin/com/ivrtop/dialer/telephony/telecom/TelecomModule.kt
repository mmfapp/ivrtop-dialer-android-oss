package com.ivrtop.dialer.telephony.telecom

import com.ivrtop.dialer.telephony.sip.CallManager
import com.ivrtop.dialer.telephony.sip.ConnectionTester
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TelecomModule {
    @Binds abstract fun callManager(impl: TelecomCallManager): CallManager
    @Binds abstract fun registrationManager(impl: DefaultRegistrationManager): RegistrationManager
    @Binds abstract fun connectionTester(impl: ConnectionProber): ConnectionTester
}
