package com.radx.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radx.app.R
import com.radx.app.RadxApplication
import com.radx.app.config.ConfigImporter
import com.radx.app.core.ConnectionState
import com.radx.app.core.CoreProvider
import com.radx.app.core.TrafficMonitor
import com.radx.app.core.TunnelInfo
import com.radx.app.core.TunnelInfoStore
import com.radx.app.core.TunnelStatus
import com.radx.app.data.AppSettings
import com.radx.app.data.Profile
import com.radx.app.data.ProxyNode
import com.radx.app.data.TunnelMode
import com.radx.app.net.TrafficProbe
import com.radx.app.service.RadxVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

data class UiEvent(val message: String, val isError: Boolean = false)

/** A dead node must fail within this window, not hang the check forever. */
private const val TRAFFIC_TIMEOUT_MS = 12_000

/**
 * Everything a running tunnel baked into its config. A change to any of these needs the
 * tunnel restarted; the rest of the settings only repaint the interface.
 */
private fun tunnelSignature(settings: AppSettings): List<Any> = listOf(
    settings.tunnelMode,
    settings.allowLan,
    settings.ipv6,
    settings.preferIpv6,
    settings.localDns,
    settings.localDnsEnabled,
    settings.vpnDns,
    settings.remoteDns,
    settings.directDns,
    settings.dnsHosts,
    settings.sniffing,
    settings.routeOnly,
    settings.localProxy,
    settings.dynamicProxyPort,
    settings.proxyPort,
    settings.proxyUser,
    settings.proxyPass,
    settings.socksUdp,
    settings.muxEnabled,
    settings.muxConcurrency,
    settings.fragmentEnabled,
    settings.fragmentSize,
    settings.fragmentInterval,
    settings.tunAddress,
    settings.tunMtu,
    settings.bypassLan,
    settings.blockQuic,
    settings.logLevel,
)

data class UiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val settings: AppSettings = AppSettings(),
    val connection: ConnectionState = ConnectionState.Disconnected,
    /** Structure of the running tunnel; null while nothing runs. */
    val tunnelInfo: TunnelInfo? = null,
    val traffic: TrafficMonitor.Snapshot = TrafficMonitor.Snapshot(),
    val delays: Map<String, Int?> = emptyMap(),
    val pinging: Boolean = false,
    /**
     * Real-traffic result per node name, for every node the user has connected
     * through. A node without an entry has not been verified, which is not the same
     * as healthy.
     */
    val trafficResults: Map<String, TrafficProbe.Result> = emptyMap(),
    val trafficCheckingNode: String? = null,
    val importing: Boolean = false,
    val busyProfileId: String? = null,
    val coreBundled: Boolean = CoreProvider.isBundled,
    val coreVersion: String = "",
) {
    val selectedProfile: Profile?
        get() = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()

    val connected: Boolean get() = connection is ConnectionState.Connected

    val busy: Boolean
        get() = connection is ConnectionState.Connecting || connection is ConnectionState.Disconnecting

    val connectedNodeName: String
        get() = (connection as? ConnectionState.Connected)?.nodeName
            ?: selectedProfile?.activeNode?.name
            ?: ""
}

class RadxViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RadxApplication.store
    private val importer = ConfigImporter()
    private val trafficMonitor = TrafficMonitor()
    private var trafficJob: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            coreBundled = CoreProvider.isBundled,
            coreVersion = runCatching { CoreProvider.version() }.getOrDefault(""),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            store.profiles.collect { profiles ->
                _state.update { it.copy(profiles = profiles) }
            }
        }
        viewModelScope.launch {
            store.selectedProfileId.collect { id ->
                // Results belong to the nodes of one profile; switching profiles
                // must not show a stale verdict for a same-named node.
                _state.update { it.copy(selectedProfileId = id, trafficResults = emptyMap()) }
            }
        }
        viewModelScope.launch {
            store.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            TunnelStatus.state.collect { connection ->
                val wasConnected = _state.value.connection is ConnectionState.Connected
                _state.update { it.copy(connection = connection) }
                if (connection is ConnectionState.Connected) {
                    if (!wasConnected) {
                        trafficMonitor.reset()
                        verifyTraffic(connection)
                    }
                } else {
                    trafficJob?.cancel()
                    _state.update { it.copy(trafficCheckingNode = null) }
                }
                if (connection is ConnectionState.Failed) {
                    sendEvent(connection.message, isError = true)
                }
            }
        }
        viewModelScope.launch {
            TunnelInfoStore.info.collect { info ->
                _state.update { it.copy(tunnelInfo = info) }
            }
        }
        startTrafficLoop()
    }

    // ---------------------------------------------------------------- tunnel

    fun connect() {
        val current = _state.value
        if (!current.coreBundled) {
            sendEvent(string(R.string.error_no_core), isError = true)
            return
        }
        if (current.selectedProfile == null) {
            sendEvent(string(R.string.error_no_profile), isError = true)
            return
        }
        RadxVpnService.start(getApplication())
    }

    fun disconnect() {
        RadxVpnService.stop(getApplication())
    }

    fun reportVpnDenied() {
        sendEvent(string(R.string.error_vpn_permission), isError = true)
    }

    // --------------------------------------------------------------- imports

    fun importText(text: String, name: String? = null) {
        if (text.isBlank()) {
            sendEvent(string(R.string.error_empty_text), isError = true)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            try {
                persist(importer.importText(text, name))
            } catch (error: Throwable) {
                sendEvent(importError(error), isError = true)
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }

    fun importSubscription(url: String, name: String?) {
        if (url.isBlank()) {
            sendEvent(string(R.string.error_url_required), isError = true)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            try {
                persist(importer.importSubscription(url, name))
            } catch (error: Throwable) {
                sendEvent(importError(error), isError = true)
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }

    fun importSharedLink(url: String) {
        importText(url)
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                }
                if (text.isNullOrBlank()) {
                    sendEvent(string(R.string.error_empty_text), isError = true)
                } else {
                    persist(importer.importText(text, displayName(uri)))
                }
            } catch (error: Throwable) {
                sendEvent(importError(error), isError = true)
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }

    /**
     * Refreshes the subscriptions that have not been updated for a while. Only the
     * automatic path is silent; a manual refresh still reports what happened.
     */
    fun refreshStaleSubscriptions() {
        val now = System.currentTimeMillis()
        _state.value.profiles
            .filter { profile ->
                profile.isSubscription &&
                    now - profile.updatedAt > AppSettings.SUBSCRIPTION_MAX_AGE_MS
            }
            .forEach { profile -> refreshSubscription(profile.id, silent = true) }
    }

    fun refreshSubscription(id: String, silent: Boolean = false) {
        val profile = _state.value.profiles.firstOrNull { it.id == id } ?: return
        val url = profile.subscriptionUrl ?: return
        viewModelScope.launch {
            _state.update { it.copy(busyProfileId = id) }
            try {
                val result = importer.refresh(url)
                val selected = profile.selectedTag?.takeIf { tag -> result.nodes.any { it.tag == tag } }
                    ?: result.nodes.firstOrNull()?.tag
                store.upsertProfile(
                    profile.copy(
                        nodes = result.nodes,
                        raw = result.raw,
                        kind = result.kind,
                        selectedTag = selected,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                restartIfActive()
                if (silent) {
                    sendEvent(string(R.string.subscription_refreshed, profile.name))
                } else {
                    sendEvent(string(R.string.import_success, result.nodes.size))
                }
            } catch (error: Throwable) {
                sendEvent(importError(error), isError = true)
            } finally {
                _state.update { it.copy(busyProfileId = null) }
            }
        }
    }

    // -------------------------------------------------------------- profiles

    fun selectProfile(id: String) {
        viewModelScope.launch {
            store.selectProfile(id)
            restartIfActive()
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            val wasSelected = _state.value.selectedProfile?.id == id
            store.deleteProfile(id)
            if (wasSelected && (TunnelStatus.isConnected || TunnelStatus.isBusy)) {
                RadxVpnService.stop(getApplication())
            }
        }
    }

    fun selectNode(tag: String) {
        val profile = _state.value.selectedProfile ?: return
        viewModelScope.launch {
            store.upsertProfile(profile.copy(selectedTag = tag))
            restartIfActive()
        }
    }

    // -------------------------------------------------------------- settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val current = _state.value.settings
            val updated = transform(current)
            store.saveSettings(updated)
            // Only a change that affects the tunnel needs a reconnect.
            val tunnelChanged = tunnelSignature(current) != tunnelSignature(updated)
            if (tunnelChanged) restartIfActive()
        }
    }

    // ------------------------------------------------------------------ ping

    fun pingAll() {
        val profile = _state.value.selectedProfile ?: return
        if (profile.nodes.isEmpty() || _state.value.pinging) return
        val timeout = _state.value.settings.pingTimeoutMs
        viewModelScope.launch {
            _state.update { it.copy(pinging = true) }
            val results = withContext(Dispatchers.IO) {
                profile.nodes.map { node ->
                    async { node.tag to pingNode(node, timeout) }
                }.awaitAll()
            }
            val delays = results.mapNotNull { (tag, delay) -> delay?.let { tag to it } }.toMap()
            _state.update { current ->
                current.copy(delays = current.delays + delays, pinging = false)
            }
        }
    }

    // --------------------------------------------------------------- traffic

    /**
     * Downloads through the live tunnel once it is up.
     *
     * A node can answer a handshake and still carry nothing, so the app verifies with
     * real traffic and shows the number (or its absence) instead of a green latency.
     * In VPN mode the app's own sockets already leave through the tunnel; in proxy mode
     * the request is sent to the local proxy port.
     */
    private fun verifyTraffic(connection: ConnectionState.Connected) {
        trafficJob?.cancel()
        trafficJob = viewModelScope.launch {
            _state.update { it.copy(trafficCheckingNode = connection.nodeName) }
            val port = if (connection.mode == TunnelMode.PROXY) connection.proxyPort else null
            val result = withContext(Dispatchers.IO) {
                TrafficProbe.measure(
                    proxyPort = port,
                    url = TrafficProbe.mobileUrl,
                    timeoutMs = TRAFFIC_TIMEOUT_MS,
                )
            }
            _state.update {
                it.copy(
                    trafficCheckingNode = null,
                    trafficResults = it.trafficResults + (connection.nodeName to result),
                )
            }
        }
    }

    // --------------------------------------------------------------- helpers

    private suspend fun persist(result: ConfigImporter.Imported) {
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = result.suggestedName,
            kind = result.kind,
            raw = result.raw,
            subscriptionUrl = result.subscriptionUrl,
            nodes = result.nodes,
            selectedTag = result.nodes.firstOrNull()?.tag,
            updatedAt = System.currentTimeMillis(),
        )
        store.upsertProfile(profile)
        store.selectProfile(profile.id)
        restartIfActive()
        sendEvent(string(R.string.import_success, result.nodes.size))
        result.warnings.firstOrNull()?.let { warning -> sendEvent(warning) }
    }

    private fun restartIfActive() {
        if (TunnelStatus.isConnected || TunnelStatus.isBusy) {
            RadxVpnService.restart(getApplication())
        }
    }

    private fun startTrafficLoop() {
        viewModelScope.launch {
            var connectedSeen = false
            while (true) {
                val connected = TunnelStatus.isConnected
                if (connected) {
                    if (!connectedSeen) {
                        trafficMonitor.reset()
                        connectedSeen = true
                    }
                    val snapshot = trafficMonitor.sample()
                    _state.update { it.copy(traffic = snapshot) }
                } else if (connectedSeen) {
                    connectedSeen = false
                    _state.update { it.copy(traffic = TrafficMonitor.Snapshot()) }
                }
                delay(1000)
            }
        }
    }

    private fun pingNode(node: ProxyNode, timeoutMs: Int): Int? {
        if (node.server.isBlank() || node.port !in 1..65535) return null
        return runCatching {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(node.server, node.port), timeoutMs)
            }
            ((System.nanoTime() - start) / 1_000_000L).toInt()
        }.getOrNull()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, null, null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun importError(error: Throwable): String =
        string(R.string.error_import_failed, error.message ?: error.javaClass.simpleName)

    private fun string(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private fun sendEvent(message: String, isError: Boolean = false) {
        viewModelScope.launch {
            _events.send(UiEvent(message, isError))
        }
    }

}
