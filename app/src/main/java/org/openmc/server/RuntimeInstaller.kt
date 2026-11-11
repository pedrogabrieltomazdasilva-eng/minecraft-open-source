package org.openmc.server

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

/** Installs only OpenJDK data; executable code remains in the APK native library directory. */
object RuntimeInstaller {
    fun ensureInstalled(context: Context, home: File, nativeDirectory: File) {
        if (complete(home)) return
        check(context.filesDir.usableSpace >= 250_000_000L) { "Espaço insuficiente para instalar o runtime Java" }
        val staging = File(context.filesDir, "jre25.staging")
        check(staging.canonicalFile.parentFile == context.filesDir.canonicalFile)
        if (staging.exists()) check(staging.deleteRecursively()) { "Unable to clear incomplete runtime staging" }
        check(staging.mkdirs()) { "Unable to create runtime staging" }
        try {
            var total = 0L
            context.assets.open("runtime-data.zip").use { asset ->
                ZipInputStream(asset).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val output = File(staging, entry.name)
                        check(output.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                            "Unsafe runtime data path"
                        }
                        if (entry.isDirectory) {
                            check(output.mkdirs() || output.isDirectory)
                        } else {
                            check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
                            output.outputStream().use { stream ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    check(total <= 512L * 1024 * 1024) { "Runtime data exceeds size limit" }
                                    stream.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            context.assets.open("runtime-native-links.txt").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.forEach { relative ->
                    check(relative.startsWith("lib/") && relative.endsWith(".so") && ".." !in relative)
                    val target = File(nativeDirectory, File(relative).name)
                    check(target.isFile) { "Missing native library: ${target.name}" }
                    val link = File(staging, relative)
                    check(link.canonicalPath.startsWith(staging.canonicalPath + File.separator))
                    check(link.parentFile?.mkdirs() == true || link.parentFile?.isDirectory == true)
                    Files.createSymbolicLink(link.toPath(), target.toPath())
                }
            }
            check(complete(staging)) { "Incomplete OpenJDK data" }
            if (home.exists()) check(home.deleteRecursively()) { "Unable to replace invalid runtime data" }
            check(staging.renameTo(home)) { "Unable to finish runtime installation" }
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun complete(home: File): Boolean =
        validModules(File(home, "lib/modules")) &&
            File(home, "lib/libjava.so").isFile &&
            File(home, "lib/server/libjvm.so").isFile &&
            File(home, "legal/java.base/LICENSE").isFile &&
            File(home, "release").takeIf { it.isFile }?.readText()?.contains("JAVA_VERSION=\"25.0.5\"") == true

    private fun validModules(modules: File): Boolean =
        modules.isFile && modules.length() == 126_800_736L &&
            modules.inputStream().use { stream ->
                val header = ByteArray(4)
                stream.read(header) == header.size &&
                    header.contentEquals(byteArrayOf(0xda.toByte(), 0xda.toByte(), 0xfe.toByte(), 0xca.toByte()))
            }
}
