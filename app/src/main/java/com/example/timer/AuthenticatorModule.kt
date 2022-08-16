package com.example.timer


import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent

import javax.inject.Named
import javax.inject.Singleton

@InstallIn(ApplicationComponent::class)
@Module
abstract class AuthenticatorModule {
    @Binds
    @Singleton
    @Named("SharedPreferences")
    abstract fun bindSharedPreferencesAuthenticator(authenticator: SharedPreferencesAuthenticator): Authenticator

    @Binds
    @Singleton
    @Named("EncryptedSharedPreferences")
    abstract fun bindEncryptedSharedPreferencesAuthenticator(authenticator: EncryptedSharedPreferencesAuthenticator): Authenticator

    @Binds
    @Singleton
    @Named("DataStorePreferences")
    abstract fun bindDataStorePreferencesAuthenticator(authenticator: DataStorePreferencesAuthenticator): Authenticator

    @Binds
    @Singleton
    @Named("EncryptedFileAuthenticator")
    abstract fun bindEncryptedFileAuthenticator(authenticator: EncryptedFileAuthenticator): Authenticator

    companion object {
        const val AUTHENTICATOR_TYPE = "SharedPreferences"
    }
}