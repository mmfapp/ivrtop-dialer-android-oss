package com.ivrtop.dialer.core.datastore

import com.ivrtop.dialer.core.models.DndConfig
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val tag: String?) { SYSTEM(null), HEBREW("he"), ENGLISH("en") }
enum class StartTab { FAVORITES, RECENTS, DIALER, CONTACTS }

/**
 * How the app stays reachable for incoming calls (docs/BACKGROUND_EXECUTION.md).
 * There is no push server, so receiving calls while the app is closed requires PERSISTENT.
 *  PERSISTENT      – foreground service keeps SIP registered (default).
 *  FOREGROUND_ONLY – registered only while the app is open (saves battery, no background calls).
 */
enum class ConnectivityMode { PERSISTENT, FOREGROUND_ONLY }

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val startTab: StartTab = StartTab.DIALER,
    val dialpadTones: Boolean = true,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val showHebrewLettersOnDialpad: Boolean = true,
    val defaultAccountId: String? = null,
    val askAccountEachCall: Boolean = false,
    val callWaiting: Boolean = true,
    val proximitySensor: Boolean = true,
    val vibrateOnRing: Boolean = true,
    val connectivityMode: ConnectivityMode = ConnectivityMode.PERSISTENT,
    val wifiOnly: Boolean = false,
    val echoCancellation: Boolean = true,
    val noiseSuppression: Boolean = true,
    val autoGainControl: Boolean = true,
    val appLock: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val batteryHintDismissed: Boolean = false,
    val contactsUploadConsent: Boolean = false,
    val routeEmergencyViaSim: Boolean = true,
    val speedDial: Map<Int, String> = emptyMap(),
    val dnd: DndConfig = DndConfig(),
    val verboseSipLogging: Boolean = false,
)
