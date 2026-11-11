package org.openmc.server

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Read-only release metadata from Mojang; server software compatibility is a separate concern. */
internal object MinecraftCatalog {
    private const val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    data class Catalog(val latest: String, val releases: List<String>, val metadata: Map<String, String>)

    fun fetch(): Catalog {
        val json = JSONObject(read(manifestUrl, 4_000_000))
        val latest = json.getJSONObject("latest").getString("release")
        val versions = json.getJSONArray("versions")
        val releases = mutableListOf<String>()
        val metadata = mutableMapOf<String, String>()
        for (index in 0 until versions.length()) {
            val entry = versions.getJSONObject(index)
            if (entry.optString("type") == "release") {
                val id = entry.getString("id")
                releases += id
                metadata[id] = entry.getString("url")
            }
        }
        require(latest in metadata) { "Versão estável ausente do catálogo oficial" }
        return Catalog(latest, releases, metadata)
    }

    fun requiredJava(catalog: Catalog, version: String): Int {
        val url = catalog.metadata[version] ?: error("Versão não encontrada no catálogo oficial")
        return JSONObject(read(url, 4_000_000)).getJSONObject("javaVersion").getInt("majorVersion")
    }

    private fun read(address: String, maxBytes: Int): String {
        val url = URL(address)
        require(url.protocol == "https" && url.host in setOf("piston-meta.mojang.com", "launchermeta.mojang.com"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            requestMethod = "GET"
        }
        try {
            check(connection.responseCode == 200) { "Catálogo indisponível (HTTP ${connection.responseCode})" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= maxBytes) { "Resposta excede o tamanho permitido" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}
