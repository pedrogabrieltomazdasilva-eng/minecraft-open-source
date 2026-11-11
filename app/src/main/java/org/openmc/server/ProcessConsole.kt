package org.openmc.server

import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

enum class ServerState { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

data class LaunchSpec(
    val command: List<String>,
    val directory: File,
    val environment: Map<String, String> = emptyMap()
)

/** Owns one child process and its console. Callbacks may run on worker threads. */
class ProcessConsole(
    private val onLine: (String) -> Unit,
    private val onState: (ServerState) -> Unit,
    private val onExit: (Int) -> Unit
) {
    private var process: Process? = null
    private var input: BufferedWriter? = null
    @Volatile var state: ServerState = ServerState.STOPPED
        private set

    fun start(spec: LaunchSpec) {
        require(spec.command.isNotEmpty() && spec.command.none { it.isBlank() })
        require(spec.directory.isDirectory)
        synchronized(this) {
            check(process == null && state != ServerState.STARTING) { "Process already running" }
            state = ServerState.STARTING
        }
        onState(ServerState.STARTING)
        val child = try {
            ProcessBuilder(spec.command)
                .directory(spec.directory)
                .redirectErrorStream(true)
                .apply { environment().putAll(spec.environment) }
                .start()
        } catch (error: Exception) {
            synchronized(this) { state = ServerState.FAILED }
            onLine("Falha ao iniciar: ${error.message}")
            onState(ServerState.FAILED)
            throw error
        }
        synchronized(this) {
            process = child
            input = BufferedWriter(OutputStreamWriter(child.outputStream, Charsets.UTF_8))
            state = ServerState.RUNNING
        }
        onState(ServerState.RUNNING)
        Thread({
            try {
                child.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach(onLine) }
            } catch (error: Exception) {
                onLine("Erro ao ler o console: ${error.message}")
            } finally {
                val code = child.waitFor()
                synchronized(this) {
                    if (process === child) {
                        try { input?.close() } catch (_: Exception) { }
                        input = null
                        process = null
                        state = ServerState.STOPPED
                    }
                }
                onExit(code)
                onState(ServerState.STOPPED)
            }
        }, "server-console-reader").start()
    }

    @Synchronized fun send(line: String) {
        require('\n' !in line && '\r' !in line)
        check(state == ServerState.RUNNING) { "Process not running" }
        val writer = checkNotNull(input)
        writer.write(line)
        writer.newLine()
        writer.flush()
    }

    fun requestStop() {
        val child: Process
        synchronized(this) {
            check(state == ServerState.RUNNING) { "Process not running" }
            child = checkNotNull(process)
            state = ServerState.STOPPING
            try {
                input?.apply { write("stop"); newLine(); flush() }
            } catch (error: Exception) {
                onLine("Falha ao enviar stop: ${error.message}")
            }
        }
        onState(ServerState.STOPPING)
        Thread({
            if (!child.waitFor(20, TimeUnit.SECONDS)) {
                onLine("Tempo de parada esgotado; encerrando o processo.")
                child.destroy()
                if (!child.waitFor(5, TimeUnit.SECONDS)) child.destroyForcibly()
            }
        }, "server-stop-watchdog").start()
    }
}
