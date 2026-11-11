package org.openmc.server

import android.content.Context
import android.os.Build
import java.io.File

/** Describes an APK-packaged native launcher and a private, non-executable JRE data tree. */
class AndroidRuntime(private val context: Context) {
    private val nativeDirectory = File(context.applicationInfo.nativeLibraryDir)
    private val launcher = File(nativeDirectory, "libopenmc_launcher.so")
    private val home = File(context.filesDir, "jre25")
    private val bootstrap = File(context.filesDir, "openmc-bootstrap.jar")

    fun unavailableReason(): String? = when {
        Build.SUPPORTED_ABIS.none { it == "arm64-v8a" } -> "É necessário um dispositivo Android ARM64."
        !launcher.isFile -> "Launcher nativo ARM64 ainda não está integrado ao APK."
        !File(nativeDirectory, "libjvm.so").isFile -> "Biblioteca libjvm.so ausente do APK."
        !File(nativeDirectory, "libjli.so").isFile -> "Biblioteca libjli.so ausente do APK."
        !context.assets.list("").orEmpty().contains("runtime-data.zip") -> "Dados do OpenJDK 25 ausentes do APK."
        else -> null
    }

    fun launch(serverJar: File, ramMb: Int): LaunchSpec {
        require(serverJar.isFile) { "JAR do servidor ausente" }
        require(ramMb in 512..6144)
        prepare()
        return LaunchSpec(
            command = listOf(launcher.absolutePath, serverJar.absolutePath, "nogui"),
            directory = serverJar.parentFile ?: error("Pasta do servidor ausente"),
            environment = environment() + ("OPENMC_HEAP_MB" to ramMb.toString())
        )
    }

    fun selfTest(): LaunchSpec {
        prepare()
        return LaunchSpec(
            command = listOf(launcher.absolutePath, "--self-test"),
            directory = context.cacheDir,
            environment = environment()
        )
    }

    private fun prepare() {
        check(unavailableReason() == null) { unavailableReason() ?: "Runtime indisponível" }
        RuntimeInstaller.ensureInstalled(context, home, nativeDirectory)
        if (!bootstrap.isFile) {
            context.assets.open("openmc-bootstrap.jar").use { source ->
                bootstrap.outputStream().use { target -> source.copyTo(target) }
            }
        }
    }

    private fun environment(): Map<String, String> = mapOf(
        "JAVA_HOME" to home.absolutePath,
        // Consumed by the project-specific OpenJDK build during early VM initialization.
        "OPENMC_RUNTIME_HOME" to home.absolutePath,
        "OPENMC_NATIVE_DIR" to nativeDirectory.absolutePath,
        "OPENMC_BOOTSTRAP_JAR" to bootstrap.absolutePath,
        "LD_LIBRARY_PATH" to nativeDirectory.absolutePath,
        "TMPDIR" to context.cacheDir.absolutePath
        )
}
