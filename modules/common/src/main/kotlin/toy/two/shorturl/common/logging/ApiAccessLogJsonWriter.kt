package toy.two.shorturl.common.logging

import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ApiAccessLogJsonWriter(
    private val objectMapper: ObjectMapper,
    private val path: Path,
) {
    private val lock = Any()

    fun write(entry: ApiAccessLogEntry) {
        val parent = path.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }

        val line = objectMapper.writeValueAsString(entry.toJsonMap()) + System.lineSeparator()
        synchronized(lock) {
            Files.writeString(
                path,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }
}
