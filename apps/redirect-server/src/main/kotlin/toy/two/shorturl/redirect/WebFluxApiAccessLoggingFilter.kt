package toy.two.shorturl.redirect

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import toy.two.shorturl.common.logging.ApiAccessLogEntry
import toy.two.shorturl.common.logging.ApiAccessLogJsonWriter
import toy.two.shorturl.common.logging.statusFamily
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class WebFluxApiAccessLoggingFilter(
    private val applicationName: String,
    private val writer: ApiAccessLogJsonWriter,
    private val includeQueryString: Boolean = true,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange)
        }

        val startedAt = Instant.now()
        val startedNanos = System.nanoTime()
        val traceId = traceId(exchange)
        val failure = AtomicReference<Throwable?>()

        exchange.response.headers.set(TRACE_ID_HEADER, traceId)

        return chain.filter(exchange)
            .doOnError { failure.set(it) }
            .doFinally {
                val status = exchange.response.statusCode?.value()
                    ?: if (failure.get() == null) HttpStatus.OK.value() else HttpStatus.INTERNAL_SERVER_ERROR.value()

                writeSafely(
                    ApiAccessLogEntry(
                        timestamp = startedAt,
                        application = applicationName,
                        method = exchange.request.method.name(),
                        uri = requestUri(exchange),
                        path = truncate(path, MAX_PATH_LENGTH) ?: "",
                        query = truncate(exchange.request.uri.rawQuery, MAX_QUERY_LENGTH),
                        status = status,
                        statusFamily = statusFamily(status),
                        durationMs = elapsedMillis(startedNanos),
                        remoteAddr = truncate(remoteAddr(exchange), MAX_REMOTE_ADDR_LENGTH),
                        userAgent = truncate(exchange.request.headers.getFirst("User-Agent"), MAX_USER_AGENT_LENGTH),
                        traceId = traceId,
                    ),
                )
            }
    }

    private fun writeSafely(entry: ApiAccessLogEntry) {
        runCatching { writer.write(entry) }
            .onFailure { log.warn("Failed to write API access log", it) }
    }

    private fun requestUri(exchange: ServerWebExchange): String {
        val path = exchange.request.path.pathWithinApplication().value()
        val query = truncate(exchange.request.uri.rawQuery, MAX_QUERY_LENGTH)
        val uri = if (includeQueryString && !query.isNullOrBlank()) {
            "$path?$query"
        } else {
            path
        }

        return truncate(uri, MAX_URI_LENGTH) ?: ""
    }

    private fun traceId(exchange: ServerWebExchange): String =
        truncate(
            exchange.request.headers.getFirst(TRACE_ID_HEADER)
            ?: exchange.request.headers.getFirst(B3_TRACE_ID_HEADER)
            ?: UUID.randomUUID().toString(),
            MAX_TRACE_ID_LENGTH,
        ) ?: UUID.randomUUID().toString()

    private fun remoteAddr(exchange: ServerWebExchange): String? =
        exchange.request.headers.getFirst("X-Forwarded-For")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: exchange.request.remoteAddress?.address?.hostAddress

    private fun elapsedMillis(startedNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos).coerceAtLeast(0)

    private fun truncate(value: String?, maxLength: Int): String? {
        if (value == null || value.length <= maxLength) {
            return value
        }

        return value.take(maxLength) + TRUNCATED_SUFFIX
    }

    companion object {
        private const val TRACE_ID_HEADER = "X-Request-Id"
        private const val B3_TRACE_ID_HEADER = "X-B3-TraceId"
        private const val MAX_URI_LENGTH = 2048
        private const val MAX_PATH_LENGTH = 512
        private const val MAX_QUERY_LENGTH = 1024
        private const val MAX_USER_AGENT_LENGTH = 512
        private const val MAX_TRACE_ID_LENGTH = 128
        private const val MAX_REMOTE_ADDR_LENGTH = 128
        private const val TRUNCATED_SUFFIX = "...[truncated]"
        private val log = LoggerFactory.getLogger(WebFluxApiAccessLoggingFilter::class.java)
    }
}
