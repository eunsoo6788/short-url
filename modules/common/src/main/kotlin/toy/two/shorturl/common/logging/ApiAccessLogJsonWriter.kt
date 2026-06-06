package toy.two.shorturl.common.logging

import tools.jackson.databind.ObjectMapper
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ApiAccessLogJsonWriter(
    private val objectMapper: ObjectMapper,
    private val path: Path,
) : AutoCloseable {
    private val lock = Any()
    private val writer: BufferedWriter

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
        synchronized(lock) {
            writer.write(line)
            writer.flush()
        }
    }

    override fun close() {
        synchronized(lock) {
            writer.close()
        }
    }
}
