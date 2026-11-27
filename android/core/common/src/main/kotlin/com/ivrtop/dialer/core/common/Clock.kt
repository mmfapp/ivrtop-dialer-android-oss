package com.ivrtop.dialer.core.common

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface Clock {
    fun now(): Long
    fun elapsedRealtime(): Long
}

class SystemClockImpl @Inject constructor() : Clock {
    override fun now(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds abstract fun clock(impl: SystemClockImpl): Clock
}
