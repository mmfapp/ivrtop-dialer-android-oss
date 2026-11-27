package com.ivrtop.dialer.core.security

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds abstract fun credentialStore(impl: KeystoreCredentialStore): SecureCredentialStore
}
