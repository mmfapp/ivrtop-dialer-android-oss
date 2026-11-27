package com.ivrtop.dialer.telephony.audio

import com.ivrtop.dialer.core.models.AudioEndpoint
import com.ivrtop.dialer.core.models.AudioRoute
import com.ivrtop.dialer.telephony.sip.AudioRouteManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio routing is owned by Android Telecom (via Core-Telecom's CallControlScope:
 * availableEndpoints / currentCallEndpoint / requestEndpointChange). Telecom handles
 * Bluetooth SCO/LE Audio, wired headsets, car kits and headset-button answer.
 * This holder mirrors Telecom's view for UI and proximity logic.
 */
@Singleton
class AudioRouteState @Inject constructor(private val proximity: ProximityController) : AudioRouteManager {
    private val _route = MutableStateFlow(AudioRoute.EARPIECE)
    private val _endpoints = MutableStateFlow<List<AudioEndpoint>>(emptyList())
    override val currentRoute: StateFlow<AudioRoute> = _route.asStateFlow()
    override val availableEndpoints: StateFlow<List<AudioEndpoint>> = _endpoints.asStateFlow()

    var proximityAllowed: Boolean = true

    fun update(route: AudioRoute, endpoints: List<AudioEndpoint>, callActive: Boolean) {
        _route.value = route
        _endpoints.value = endpoints
        proximity.setEnabled(callActive && proximityAllowed && route == AudioRoute.EARPIECE)
    }

    fun reset() {
        _route.value = AudioRoute.EARPIECE
        _endpoints.value = emptyList()
        proximity.setEnabled(false)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds abstract fun audioRouteManager(impl: AudioRouteState): AudioRouteManager
}
