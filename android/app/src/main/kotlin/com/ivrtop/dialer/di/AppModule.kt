package com.ivrtop.dialer.di

import android.os.Build
import com.ivrtop.dialer.BuildConfig
import com.ivrtop.dialer.core.common.BuildInfo
import com.ivrtop.dialer.telephony.telecom.CallUiIntents
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvides {
    @Provides @Singleton
    fun buildInfo(): BuildInfo = BuildInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        debug = BuildConfig.DEBUG,
        appLinkHost = BuildConfig.APP_LINK_HOST,
        appLinkPath = BuildConfig.APP_LINK_PATH,
        defaultSipDomain = BuildConfig.DEFAULT_SIP_DOMAIN,
        linkAllowedDomains = BuildConfig.LINK_ALLOWED_DOMAINS.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
        // No device identifiers in the UA – only product, version and OS level.
        userAgent = "IVRTOP-Dialer/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})",
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBinds {
    @Binds abstract fun callUiIntents(impl: CallUiIntentsImpl): CallUiIntents
}
