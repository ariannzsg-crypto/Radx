package com.radx.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.radx.app.MainActivity
import com.radx.app.R
import com.radx.app.config.SingBoxConfigBuilder
import com.radx.app.config.XrayBridge
import com.radx.app.config.XrayConfigBuilder
import com.radx.app.core.ConnectionState
import com.radx.app.core.CoreEngine
import com.radx.app.core.CoreLog
import com.radx.app.core.LogSource
import com.radx.app.core.CoreProvider
import com.radx.app.core.TunSetup
import com.radx.app.core.TunSpec
import com.radx.app.core.TunnelInfo
import com.radx.app.core.TunnelInfoStore
import com.radx.app.core.TunnelStatus
import com.radx.app.core.XrayRunner
import com.radx.app.data.AppSettings
import com.radx.app.data.Profile
import com.radx.app.data.RadxStore
import com.radx.app.data.TunnelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Hosts the proxy core.
 *
 * In VPN mode the core asks for a tun file descriptor through [TunSetup] and all
 * device traffic is routed through it. In proxy mode no tun is created and the core
 * exposes a local SOCKS/HTTP port instead, so the service works without VPN consent.
 */
class RadxVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: RadxStore

    private var engine: CoreEngine? = null
    private var xraySession: XrayRunner.Session? = null
    private var tunInterface: ParcelFileDescriptor? = null
    private var startJob: Job? = null

    /** What the core asked the system for, kept so the UI can show the real interface. */
    @Volatile
    private var lastTunSpec: TunSpec? = null

    override fun onCreate() {
        super.onCreate()
        store = RadxStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTunnel()
            ACTION_RESTART -> startTunnel()
            else -> {
                val state = TunnelStatus.value
                if (state is ConnectionState.Connected || state is ConnectionState.Connecting) {
                    // Already running: avoid tearing the tunnel down on repeated starts.
                    return START_STICKY
                }
                startTunnel()
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked, stopping tunnel")
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        startJob?.cancel()
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ lifecycle

    private fun startTunnel() {
        // startForeground must happen right away when the service was started with
        // startForegroundService(), even if the core turns out to be missing.
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_connecting), ""),
        )

        if (!CoreProvider.isBundled) {
            TunnelStatus.update(ConnectionState.Failed(getString(R.string.error_no_core)))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        TunnelStatus.update(ConnectionState.Connecting)
        startJob?.cancel()
        startJob = scope.launch {
            teardown()

            val profile = store.readSelectedProfile()
            if (profile == null) {
                fail(getString(R.string.error_no_profile))
                return@launch
            }
            val settings = store.readSettings()

            try {
                // Nodes whose transport only Xray understands run there, and sing-box
                // reaches them through their local SOCKS port.
                val xrayConfig = XrayConfigBuilder.build(profile.nodes)
                var xrayReady = false
                if (xrayConfig != null) {
                    if (XrayRunner.isAvailable(this@RadxVpnService)) {
                        val firstXrayIndex = profile.nodes.indexOfFirst { it.viaXray }
                        xraySession = XrayRunner.start(
                            this@RadxVpnService,
                            xrayConfig,
                            XrayBridge.port(firstXrayIndex.coerceAtLeast(0)),
                        )
                        xrayReady = true
                    } else {
                        Log.w(TAG, "xray core is not part of this build")
                        CoreLog.add(
                            LogSource.XRAY,
                            "core missing from this build: ${profile.nodes.count { it.viaXray }} node(s) skipped",
                        )
                    }
                }

                val config = SingBoxConfigBuilder.build(profile, settings, xrayAvailable = xrayReady)
                val implementation = CoreProvider.create()
                    ?: throw IllegalStateException(getString(R.string.error_no_core))

                implementation.setup(
                    baseDirectory = filesDir.absolutePath,
                    workingDirectory = directory("singbox").absolutePath,
                    tempDirectory = File(cacheDir, "singbox").apply { mkdirs() }.absolutePath,
                )
                implementation.start(
                    configJson = config,
                    tun = if (settings.tunnelMode == TunnelMode.VPN) tunSetup else null,
                )
                engine = implementation

                publishTunnelInfo(profile, settings, xrayReady)

                val nodeName = SingBoxConfigBuilder.activeNode(profile, xrayReady)?.name ?: profile.name
                TunnelStatus.update(
                    ConnectionState.Connected(
                        profileId = profile.id,
                        profileName = profile.name,
                        nodeName = nodeName,
                        mode = settings.tunnelMode,
                        since = System.currentTimeMillis(),
                        proxyPort = if (settings.tunnelMode == TunnelMode.PROXY) {
                            SingBoxConfigBuilder.PROXY_PORT
                        } else {
                            0
                        },
                    ),
                )
                if (settings.showNotification) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(
                        NOTIFICATION_ID,
                        buildNotification(
                            getString(R.string.status_connected),
                            getString(R.string.notification_connected, nodeName),
                        ),
                    )
                }
            } catch (error: Throwable) {
                Log.e(TAG, "failed to start tunnel", error)
                teardown()
                fail(error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private fun stopTunnel() {
        if (TunnelStatus.value === ConnectionState.Disconnected) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        TunnelStatus.update(ConnectionState.Disconnecting)
        startJob?.cancel()
        startJob = scope.launch {
            teardown()
            TunnelStatus.update(ConnectionState.Disconnected)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun teardown() {
        runCatching { engine?.stop() }.onFailure { Log.w(TAG, "core stop failed", it) }
        engine = null
        XrayRunner.stop(xraySession)
        xraySession = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        lastTunSpec = null
        TunnelInfoStore.clear()
    }

    /**
     * Records the structure the tunnel ended up with: interface, routes, DNS, per-app
     * scope and the Xray bridge. Shown in the app and in the log, which makes a failing
     * setup readable without a debug build.
     */
    private fun publishTunnelInfo(profile: Profile, settings: AppSettings, xrayReady: Boolean) {
        val spec = lastTunSpec
        val xrayIndexes = profile.nodes.withIndex().filter { it.value.viaXray }.map { it.index }
        val info = TunnelInfo(
            mode = settings.tunnelMode,
            core = runCatching { engine?.version }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "sing-box",
            mtu = spec?.mtu ?: 0,
            ipv4Addresses = spec?.ipv4Addresses.orEmpty(),
            ipv6Addresses = spec?.ipv6Addresses.orEmpty(),
            ipv4Routes = spec?.ipv4Routes.orEmpty(),
            ipv6Routes = spec?.ipv6Routes.orEmpty(),
            dnsServers = listOfNotNull(spec?.dnsAddress?.substringBefore('/')),
            autoRoute = spec?.autoRoute ?: false,
            strictRoute = spec?.strictRoute ?: false,
            includedApps = spec?.includePackages.orEmpty(),
            excludedApps = spec?.excludePackages.orEmpty(),
            xrayNodes = if (xrayReady) xrayIndexes.size else 0,
            xrayPorts = if (xrayReady) xrayIndexes.map { XrayBridge.port(it) } else emptyList(),
            proxyPort = if (settings.tunnelMode == TunnelMode.PROXY) {
                SingBoxConfigBuilder.PROXY_PORT
            } else {
                0
            },
        )
        TunnelInfoStore.set(info)
        CoreLog.add(
            LogSource.SING_BOX,
            "tunnel ready: mode=${settings.tunnelMode} mtu=${info.mtu} " +
                "addr=${info.addressLines.joinToString(",")} " +
                "routes=${info.routeLines.size} dns=${info.dnsServers.joinToString(",")} " +
                "xray=${info.xrayNodes}",
        )
    }

    private fun fail(message: String) {
        CoreLog.add(LogSource.SING_BOX, message)
        TunnelStatus.update(ConnectionState.Failed(message))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun directory(name: String): File = File(filesDir, name).apply { mkdirs() }

    // ------------------------------------------------------------------ tun

    private val tunSetup = object : TunSetup {
        override fun protect(fd: Int): Boolean = this@RadxVpnService.protect(fd)

        override fun establish(spec: TunSpec): Int {
            lastTunSpec = spec
            val builder = Builder()
            builder.setSession(getString(R.string.app_name))
            builder.setMtu(if (spec.mtu in 1280..65535) spec.mtu else 9000)
            builder.setBlocking(true)

            spec.ipv4Addresses.forEach { addAddress(builder, it, 32) }
            spec.ipv6Addresses.forEach { addAddress(builder, it, 128) }

            var routeCount = 0
            spec.ipv4Routes.forEach { routeCount += addRoute(builder, it, 0) }
            spec.ipv6Routes.forEach { routeCount += addRoute(builder, it, 0) }
            if (routeCount == 0) {
                builder.addRoute("0.0.0.0", 0)
                if (spec.ipv6Routes.isNotEmpty()) builder.addRoute("::", 0)
            }

            spec.dnsAddress?.let { dns ->
                runCatching { builder.addDnsServer(dns.substringBefore('/')) }
            }

            spec.includePackages.forEach { pkg ->
                runCatching { builder.addAllowedApplication(pkg) }
                    .onFailure { Log.w(TAG, "cannot include $pkg", it) }
            }
            // Never route our own sockets back into the tunnel: the core needs a
            // direct path to the upstream server.
            runCatching { builder.addDisallowedApplication(packageName) }
            spec.excludePackages.filterNot { it == packageName }.forEach { pkg ->
                runCatching { builder.addDisallowedApplication(pkg) }
                    .onFailure { Log.w(TAG, "cannot exclude $pkg", it) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val descriptor = builder.establish()
                ?: throw IllegalStateException("Unable to establish the VPN interface")
            tunInterface = descriptor
            return descriptor.fd
        }
    }

    private fun addAddress(builder: Builder, cidr: String, defaultPrefix: Int) {
        val address = cidr.substringBefore('/')
        val prefix = cidr.substringAfter('/', defaultPrefix.toString()).toIntOrNull() ?: defaultPrefix
        runCatching { builder.addAddress(address, prefix) }
            .onFailure { Log.w(TAG, "invalid address $cidr", it) }
    }

    private fun addRoute(builder: Builder, cidr: String, defaultPrefix: Int): Int {
        val address = cidr.substringBefore('/')
        val prefix = cidr.substringAfter('/', defaultPrefix.toString()).toIntOrNull() ?: defaultPrefix
        return runCatching {
            builder.addRoute(address, prefix)
            1
        }.getOrElse {
            Log.w(TAG, "invalid route $cidr", it)
            0
        }
    }

    // --------------------------------------------------------- notification

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RadxVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_action_disconnect), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "RadxVpnService"
        private const val CHANNEL_ID = "radx_tunnel"
        private const val NOTIFICATION_ID = 4711

        const val ACTION_START = "com.radx.app.action.START"
        const val ACTION_STOP = "com.radx.app.action.STOP"
        const val ACTION_RESTART = "com.radx.app.action.RESTART"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RadxVpnService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RadxVpnService::class.java).setAction(ACTION_STOP),
            )
        }

        fun restart(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RadxVpnService::class.java).setAction(ACTION_RESTART),
            )
        }
    }
}
