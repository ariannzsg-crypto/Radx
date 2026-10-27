package com.radx.desktop

import com.radx.app.data.AppSettings
import com.radx.app.data.Profile
import com.radx.app.data.TunnelMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Files the desktop tool keeps: state in `~/.radx`, generated configs in `run/`. */
object DesktopPaths {

    val home: Path = System.getenv("RADX_HOME")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
        ?: Paths.get(System.getProperty("user.home"), ".radx")

    val state: Path get() = home.resolve("config.json")

    val run: Path get() = home.resolve("run")
}

@Serializable
data class DesktopState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    // A desktop has no VPN service to ask first, so proxy mode is the default here.
    val settings: AppSettings = AppSettings(tunnelMode = TunnelMode.PROXY),
) {
    val selectedProfile: Profile?
        get() = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
}

/** The whole state is one JSON file, so a profile can be shared by copying it. */
class DesktopStore(private val file: Path = DesktopPaths.state) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun read(): DesktopState {
        if (!Files.isRegularFile(file)) return DesktopState()
        return runCatching { json.decodeFromString(DesktopState.serializer(), Files.readString(file)) }
            .getOrElse { throw IllegalStateException("Cannot read ${file}: ${it.message}") }
    }

    fun write(state: DesktopState) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(DesktopState.serializer(), state))
    }

    fun upsert(profile: Profile) {
        val current = read()
        val profiles = if (current.profiles.any { it.id == profile.id }) {
            current.profiles.map { if (it.id == profile.id) profile else it }
        } else {
            current.profiles + profile
        }
        write(
            current.copy(
                profiles = profiles,
                selectedProfileId = current.selectedProfileId ?: profile.id,
            ),
        )
    }
}
