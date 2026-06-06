package toy.two.shorturl.common.logging

import tools.jackson.databind.ObjectMapper
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ApiAccessLogJsonWriter(
    private val objectMapper: ObjectMapper,
    private val path: Path,
    private val flushEveryLines: Int = 1,
    asyncEnabled: Boolean = false,
    queueCapacity: Int = 8192,
) : AutoCloseable {
    init {
        require(flushEveryLines > 0) { "flushEveryLines must be greater than 0" }
        require(queueCapacity > 0) { "queueCapacity must be greater than 0" }
    }

    private val lock = Any()
    private val writer: BufferedWriter
    private var pendingFlushLines = 0
    private val droppedLines = AtomicLong()
    @Volatile
    private var running = true
    private val queue = if (asyncEnabled) ArrayBlockingQueue<String>(queueCapacity) else null
    private val worker = queue?.let { logQueue ->
        Thread({ writeLoop(logQueue) }, "api-access-log-writer-${path.fileName}").apply {
            isDaemon = true
            start()
        }
    }

    init {
        val parent = path.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        writer = Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    fun write(entry: ApiAccessLogEntry) {
        val line = objectMapper.writeValueAsString(entry.toJsonMap()) + System.lineSeparator()
        val logQueue = queue
        if (logQueue == null) {
            writeLine(line)
            return
        }

        if (!logQueue.offer(line)) {
            droppedLines.incrementAndGet()
        }
    }

    fun droppedLineCount(): Long = droppedLines.get()

    private fun writeLoop(logQueue: ArrayBlockingQueue<String>) {
        while (running || logQueue.isNotEmpty()) {
            val line = logQueue.poll(WORKER_POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) ?: continue
            writeLine(line)
        }
    }

    override fun close() {
        running = false
        worker?.join(WORKER_JOIN_TIMEOUT_MILLIS)
        synchronized(lock) {
            queue?.let {
                while (true) {
                    val line = it.poll() ?: break
                    writeLineWithoutLock(line)
                }
            }
            flushWithoutLock()
            writer.close()
        }
    }

    private fun writeLine(line: String) {
        synchronized(lock) {
            writeLineWithoutLock(line)
        }
    }

    private fun writeLineWithoutLock(line: String) {
        writer.write(line)
        pendingFlushLines += 1
        if (pendingFlushLines >= flushEveryLines) {
            flushWithoutLock()
        }
    }

    private fun flushWithoutLock() {
        writer.flush()
        pendingFlushLines = 0
    }

    private companion object {
        private const val WORKER_POLL_TIMEOUT_MILLIS = 100L
        private const val WORKER_JOIN_TIMEOUT_MILLIS = 2_000L
    }
}
