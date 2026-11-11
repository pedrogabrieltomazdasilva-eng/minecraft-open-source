package org.openmc.server

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class ServerProfile(
    val id: String,
    var name: String,
    var software: String,
    var version: String,
    var ramMb: Int,
    var difficulty: String,
    var gameMode: String,
    var officialAuth: Boolean,
    var eulaAccepted: Boolean,
    var description: String = "",
    var identifier: String = "",
    var worldType: String = "normal",
    var lanEnabled: Boolean = true,
    var tcpPort: Int = 25565,
    var udpPort: Int = 19132,
    var geyserRequested: Boolean = false,
    var floodgateRequested: Boolean = false
)

/** Stores only user-entered server metadata; it never downloads or creates a server JAR. */
class ServerProfiles(private val context: Context) {
    private val manifest = File(context.filesDir, "server-profiles.json")

    fun all(): List<ServerProfile> {
        val stored = if (manifest.isFile) runCatching {
            val array = JSONArray(manifest.readText())
            (0 until array.length()).map { index -> array.getJSONObject(index).toProfile() }
        }.getOrElse { emptyList() } else emptyList()
        if (stored.isNotEmpty() || !File(context.filesDir, "server/server.jar").isFile) return stored
        return listOf(ServerProfile("legacy", "Meu servidor", "JAR importado", "Não informada", 1024, "normal", "survival", true, false))
    }

    fun create(profile: ServerProfile): ServerProfile {
        require(profile.id.matches(Regex("[a-zA-Z0-9-]+")))
        val profiles = all().toMutableList()
        require(profiles.none { it.id == profile.id })
        check(directory(profile).mkdirs() || directory(profile).isDirectory)
        profiles += profile
        save(profiles)
        return profile
    }

    fun update(profile: ServerProfile) {
        val profiles = all().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        require(index >= 0)
        profiles[index] = profile
        save(profiles)
    }

    fun directory(profile: ServerProfile): File =
        if (profile.id == "legacy") File(context.filesDir, "server")
        else File(File(context.filesDir, "servers"), profile.id)

    fun jar(profile: ServerProfile): File = File(directory(profile), "server.jar")

    private fun save(profiles: List<ServerProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject().apply {
                put("id", profile.id); put("name", profile.name); put("software", profile.software)
                put("version", profile.version); put("ramMb", profile.ramMb)
                put("difficulty", profile.difficulty); put("gameMode", profile.gameMode)
                put("officialAuth", profile.officialAuth); put("eulaAccepted", profile.eulaAccepted)
                put("description", profile.description); put("identifier", profile.identifier)
                put("worldType", profile.worldType); put("lanEnabled", profile.lanEnabled)
                put("tcpPort", profile.tcpPort); put("udpPort", profile.udpPort)
                put("geyserRequested", profile.geyserRequested)
                put("floodgateRequested", profile.floodgateRequested)
            })
        }
        val staging = File(context.filesDir, "server-profiles.json.part")
        staging.writeText(array.toString(2))
        Files.move(staging.toPath(), manifest.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun JSONObject.toProfile() = ServerProfile(
        getString("id"), getString("name"), getString("software"), getString("version"),
        getInt("ramMb"), getString("difficulty"), getString("gameMode"),
        getBoolean("officialAuth"), getBoolean("eulaAccepted"),
        optString("description"), optString("identifier"), optString("worldType", "normal"),
        optBoolean("lanEnabled", true), optInt("tcpPort", 25565), optInt("udpPort", 19132),
        optBoolean("geyserRequested", false), optBoolean("floodgateRequested", false)
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
