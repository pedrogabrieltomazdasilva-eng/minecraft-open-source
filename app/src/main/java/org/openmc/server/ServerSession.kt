package org.openmc.server

/** Keeps the child process and recent console output across Activity recreation. */
object ServerSession {
    private val lines = ArrayDeque<String>()
    @Volatile private var listener: (() -> Unit)? = null
    @Volatile var preparing: Boolean = false
        private set
    @Volatile var activeProfileId: String? = null

    val process = ProcessConsole(
        onLine = ::note,
        onState = { listener?.invoke() },
        onExit = { code -> activeProfileId = null; note("Processo encerrado (código $code).") }
    )

    @Synchronized fun note(line: String) {
        lines.addLast(line)
        while (lines.size > 400) lines.removeFirst()
        listener?.invoke()
    }

    @Synchronized fun consoleText(): String = lines.joinToString("\n")

    @Synchronized fun beginPrepare(): Boolean {
        if (preparing || process.state !in listOf(ServerState.STOPPED, ServerState.FAILED)) return false
        preparing = true
        listener?.invoke()
        return true
    }

    @Synchronized fun endPrepare() {
        preparing = false
        listener?.invoke()
    }

    fun attach(onChange: () -> Unit) {
        listener = onChange
        onChange()
    }

    fun detach(onChange: () -> Unit) {
        if (listener === onChange) listener = null
    }
}
