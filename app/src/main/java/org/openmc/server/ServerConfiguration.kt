package org.openmc.server

import java.io.File
import java.util.Properties

/** Applies explicit local choices immediately before launching an imported server JAR. */
object ServerConfiguration {
    fun prepare(directory: File, profile: ServerProfile) {
        check(profile.eulaAccepted) { "Aceite a EULA antes de iniciar o servidor." }
        check(directory.isDirectory)
        val propertiesFile = File(directory, "server.properties")
        val properties = Properties()
        if (propertiesFile.isFile) propertiesFile.inputStream().use(properties::load)
        properties.setProperty("online-mode", profile.officialAuth.toString())
        properties.setProperty("difficulty", profile.difficulty)
        properties.setProperty("gamemode", profile.gameMode)
        require(profile.tcpPort in 1..65535)
        properties.setProperty("server-port", profile.tcpPort.toString())
        properties.setProperty("server-ip", if (profile.lanEnabled) "" else "127.0.0.1")
        propertiesFile.outputStream().use { properties.store(it, "Minecraft Open Server") }
        File(directory, "eula.txt").writeText("# Aceite registrado pelo usuário no Minecraft Open Server\neula=true\n")
    }
}
