package com.radx.app.core

import com.radx.app.data.TunnelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** UI-facing state of the tunnel, written by the service and read by the ViewModel. */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Disconnecting : ConnectionState

    data class Connected(
        val profileId: String,
        val profileName: String,
        val nodeName: String,
        val mode: TunnelMode,
        val since: Long,
        val proxyPort: Int,
    ) : ConnectionState

    data class Failed(val message: String) : ConnectionState
}

object TunnelStatus {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    val value: ConnectionState get() = _state.value

    fun update(state: ConnectionState) {
        _state.value = state
    }

    val isConnected: Boolean get() = value is ConnectionState.Connected
    val isBusy: Boolean
        get() = value is ConnectionState.Connecting || value is ConnectionState.Disconnecting
}
