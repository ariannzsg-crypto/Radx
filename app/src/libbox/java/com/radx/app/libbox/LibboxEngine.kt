package com.radx.app.libbox

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.system.OsConstants
import android.util.Log
import com.radx.app.RadxApplication
import com.radx.app.core.CoreEngine
import com.radx.app.core.CoreLog
import com.radx.app.core.LogSource
import com.radx.app.core.TunSetup
import com.radx.app.core.TunSpec
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringBox
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.Inet6Address
import java.net.InetSocketAddress
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface
import io.nekohasekai.libbox.Notification as LibboxNotification

/**
 * sing-box core, bound with gomobile.
 *
 * This file lives in the `libbox` source set: Gradle only compiles it when
 * `app/libs/libbox.aar` exists (see `scripts/build-libbox.sh` and the release
 * workflow). That keeps the project buildable without the Go core while giving the
 * released APK the real engine.
 */
class LibboxEngine : CoreEngine {

    private var boxService: BoxService? = null
    private var platform: AndroidPlatform? = null

    override val version: String
        get() = runCatching { Libbox.version() }.getOrDefault("unknown")

    override fun setup(baseDirectory: String, workingDirectory: String, tempDirectory: String) {
        val options = SetupOptions()
        options.basePath = baseDirectory
        options.workingPath = workingDirectory
        options.tempPath = tempDirectory
        options.fixAndroidStack = true
        Libbox.setup(options)
    }

    override fun start(configJson: String, tun: TunSetup?) {
        stop()
        val androidPlatform = AndroidPlatform(tun)
        platform = androidPlatform
        val service = Libbox.newService(configJson, androidPlatform)
        service.start()
        boxService = service
    }

    override fun stop() {
        val service = boxService
        boxService = null
        runCatching { service?.close() }.onFailure { Log.w(TAG, "close failed", it) }
        platform?.shutdown()
        platform = null
    }

    private companion object {
        const val TAG = "LibboxEngine"
    }
}

/** Bridges libbox's platform callbacks to Android APIs. */
private class AndroidPlatform(private val tun: TunSetup?) : PlatformInterface {

    private val context = RadxApplication.context
    private val connectivity: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var listener: InterfaceUpdateListener? = null

    // ------------------------------------------------------------ tun/socket

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        runCatching { tun?.protect(fd) }
            .onFailure { Log.w(TAG, "protect($fd) failed", it) }
    }

    override fun openTun(options: TunOptions): Int {
        val setup = tun ?: error("The core requested a tun interface but no VPN service is active")
        val spec = TunSpec(
            mtu = options.mtu,
            ipv4Addresses = options.inet4Address.readPrefixes(),
            ipv6Addresses = options.inet6Address.readPrefixes(),
            ipv4Routes = options.inet4RouteRange.readPrefixes() + options.inet4RouteAddress.readPrefixes(),
            ipv6Routes = options.inet6RouteRange.readPrefixes() + options.inet6RouteAddress.readPrefixes(),
            dnsAddress = runCatching { options.dnsServerAddress?.value }.getOrNull(),
            autoRoute = options.autoRoute,
            strictRoute = options.strictRoute,
            includePackages = options.includePackage.readStrings(),
            excludePackages = options.excludePackage.readStrings(),
        )
        return setup.establish(spec)
    }

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): Int = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        connectivity?.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort),
        ) ?: -1
    }.getOrDefault(-1)

    override fun packageNameByUid(uid: Int): String = runCatching {
        context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: ""
    }.getOrDefault("")

    override fun uidByPackageName(packageName: String): Int = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0).uid
    }.getOrDefault(-1)

    // ------------------------------------------------------------- logging

    override fun writeLog(message: String) {
        Log.i(TAG, message)
        CoreLog.add(LogSource.SING_BOX, message)
    }

    // ---------------------------------------------------- network interfaces

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        closeDefaultInterfaceMonitor(listener)
        val manager = connectivity ?: return
        this.listener = listener
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                notifyDefaultInterface(network, listener)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                notifyDefaultInterface(network, listener)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                notifyDefaultInterface(network, listener)
            }
        }
        networkCallback = callback
        runCatching {
            manager.registerDefaultNetworkCallback(callback)
            manager.activeNetwork?.let { notifyDefaultInterface(it, listener) }
        }.onFailure { Log.w(TAG, "registerDefaultNetworkCallback failed", it) }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        this.listener = null
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val interfaces = mutableListOf<LibboxNetworkInterface>()
        val manager = connectivity ?: return InterfaceIterator(emptyList())
        val systemInterfaces = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        }.getOrDefault(emptyList())

        for (network in manager.allNetworks) {
            val linkProperties = runCatching { manager.getLinkProperties(network) }.getOrNull() ?: continue
            val capabilities = runCatching { manager.getNetworkCapabilities(network) }.getOrNull() ?: continue
            val name = linkProperties.interfaceName ?: continue
            val systemInterface = systemInterfaces.firstOrNull { it.name == name } ?: continue

            val boxInterface = LibboxNetworkInterface()
            boxInterface.name = name
            boxInterface.index = systemInterface.index
            boxInterface.mtu = runCatching { systemInterface.mtu }.getOrDefault(1500)
            boxInterface.addresses = StringArray(
                runCatching {
                    systemInterface.interfaceAddresses.mapNotNull { it.toCidr() }
                }.getOrDefault(emptyList()),
            )
            boxInterface.dnsServer = StringArray(
                linkProperties.dnsServers.mapNotNull { it.hostAddress },
            )
            boxInterface.flags = systemInterface.dumpFlags(capabilities)
            boxInterface.type = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                else -> Libbox.InterfaceTypeOther
            }
            boxInterface.metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            interfaces += boxInterface
        }
        return InterfaceIterator(interfaces)
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() = Unit

    override fun readWIFIState(): WIFIState? = null

    override fun sendNotification(notification: LibboxNotification) = Unit

    fun shutdown() {
        val callback = networkCallback ?: return
        networkCallback = null
        listener = null
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }

    private fun notifyDefaultInterface(network: Network, listener: InterfaceUpdateListener) {
        val manager = connectivity ?: return
        runCatching {
            val linkProperties = manager.getLinkProperties(network) ?: return@runCatching
            val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching
            val name = linkProperties.interfaceName ?: return@runCatching
            val index = java.net.NetworkInterface.getByName(name)?.index ?: 0
            val expensive = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            listener.updateDefaultInterface(name, index, expensive, false)
        }.onFailure { Log.w(TAG, "default interface update failed", it) }
    }

    private companion object {
        const val TAG = "LibboxPlatform"
    }
}

// ------------------------------------------------------------------- helpers

private fun RoutePrefixIterator.readPrefixes(): List<String> {
    val result = mutableListOf<String>()
    while (hasNext()) {
        val prefix = next()
        result += "${prefix.address()}/${prefix.prefix()}"
    }
    return result
}

private fun StringIterator.readStrings(): List<String> {
    val result = mutableListOf<String>()
    while (hasNext()) {
        result += next()
    }
    return result
}

private class StringArray(private val values: List<String>) : StringIterator {
    private val iterator = values.iterator()

    override fun len(): Int = values.size

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): String = iterator.next()
}

private class InterfaceIterator(
    private val items: List<LibboxNetworkInterface>,
) : NetworkInterfaceIterator {

    private val iterator = items.iterator()

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): LibboxNetworkInterface = iterator.next()
}

private fun java.net.InterfaceAddress.toCidr(): String? {
    val hostAddress = address?.hostAddress ?: return null
    return if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
    } else {
        "$hostAddress/$networkPrefixLength"
    }
}

private fun java.net.NetworkInterface.dumpFlags(capabilities: NetworkCapabilities): Int {
    var flags = 0
    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
    }
    if (runCatching { isLoopback }.getOrDefault(false)) flags = flags or OsConstants.IFF_LOOPBACK
    if (runCatching { isPointToPoint }.getOrDefault(false)) flags = flags or OsConstants.IFF_POINTOPOINT
    if (runCatching { supportsMulticast() }.getOrDefault(false)) flags = flags or OsConstants.IFF_MULTICAST
    return flags
}
