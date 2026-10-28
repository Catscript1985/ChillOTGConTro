package com.chillotgcontro.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

/** Process hosted by Shizuku/Sui with shell or root identity. */
class ShizukuInputUserService : IShizukuInputService.Stub() {
    private val lock = Any()
    @Volatile private var eventProcess: Process? = null
    @Volatile private var eventThread: Thread? = null
    @Volatile private var eventListener: IShizukuEventListener? = null

    override fun startGetevent(listener: IShizukuEventListener) {
        stopGetevent()
        val process = ProcessBuilder("getevent", "-lt").redirectErrorStream(true).start()
        eventProcess = process
        eventListener = listener
        eventThread = Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                    while (!Thread.currentThread().isInterrupted && eventProcess === process) {
                        val line = reader.readLine() ?: break
                        if (INPUT_LINE.matcher(line).find()) runCatching { listener.onLine(line) }
                    }
                }
                if (eventProcess === process) {
                    val code = runCatching { process.waitFor() }.getOrDefault(-1)
                    runCatching { listener.onError("getevent dừng (mã $code)") }
                }
            } catch (t: Throwable) {
                if (eventProcess === process) runCatching { listener.onError(t.message ?: t.javaClass.simpleName) }
            }
        }, "ChillOTG-Shizuku-getevent").apply {
            isDaemon = true
            start()
        }
    }

    override fun stopGetevent() {
        synchronized(lock) {
            val process = eventProcess
            eventProcess = null
            eventListener = null
            eventThread?.interrupt()
            eventThread = null
            runCatching { process?.destroy() }
        }
    }

    override fun execute(command: Array<out String>?): String {
        val arguments = command?.map { it } ?: throw IllegalArgumentException("Lệnh shell trống")
        require(arguments.isNotEmpty()) { "Lệnh shell trống" }
        val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(1024) }
        val code = process.waitFor()
        if (code != 0) throw IllegalStateException("Lệnh thất bại ($code): ${output.take(300)}")
        return output
    }

    override fun destroy() {
        stopGetevent()
        Thread({ System.exit(0) }, "ChillOTG-Shizuku-destroy").start()
    }

    companion object {
        private val INPUT_LINE = Pattern.compile("\\b(KEY_[A-Z0-9_]+|BTN_[A-Z0-9_]+)\\s+(DOWN|UP|REPEAT)\\b")
    }
}
