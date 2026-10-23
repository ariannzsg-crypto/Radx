package com.radx.app.core

/** Everything the UI/service needs from a proxy core. */
interface CoreEngine {
    /** Prepares the core working directories. Must be called once before [start]. */
    fun setup(baseDirectory: String, workingDirectory: String, tempDirectory: String)

    /**
     * Starts the core with the given sing-box config.
     *
     * @param tun required in VPN mode; null for a proxy-only (mixed inbound) session.
     */
    fun start(configJson: String, tun: TunSetup?)

    fun stop()

    val version: String
}

/** Implemented by the VPN service so the core can build and protect its sockets. */
interface TunSetup {
    /** Protects an upstream socket from being routed back into the tunnel. */
    fun protect(fd: Int): Boolean

    /** Builds the Android VPN interface described by [spec] and returns its file descriptor. */
    fun establish(spec: TunSpec): Int
}

/** Plain representation of the tun options libbox asks for. */
data class TunSpec(
    val mtu: Int,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
    val ipv4Routes: List<String>,
    val ipv6Routes: List<String>,
    val dnsAddress: String?,
    val autoRoute: Boolean,
    val strictRoute: Boolean,
    val includePackages: List<String>,
    val excludePackages: List<String>,
)

/**
 * Loads the libbox-backed engine when the AAR is bundled.
 *
 * The implementation lives in `app/src/libbox/java`, a source set that Gradle only
 * compiles when `app/libs/libbox.aar` exists. Everything else (UI, storage,
 * parsing) builds and runs without it, which keeps the project usable before the
 * Go core has been built.
 */
object CoreProvider {

    private const val ENGINE_CLASS = "com.radx.app.libbox.LibboxEngine"

    val isBundled: Boolean by lazy {
        runCatching { Class.forName(ENGINE_CLASS) }.isSuccess
    }

    fun create(): CoreEngine? = runCatching {
        val instance = Class.forName(ENGINE_CLASS).getDeclaredConstructor().newInstance()
        instance as CoreEngine
    }.getOrNull()

    fun version(): String = create()?.version ?: "not bundled"
}
