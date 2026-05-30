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
                        path = path,
                        query = exchange.request.uri.rawQuery,
                        status = status,
                        statusFamily = statusFamily(status),
                        durationMs = elapsedMillis(startedNanos),
                        remoteAddr = remoteAddr(exchange),
                        userAgent = exchange.request.headers.getFirst("User-Agent"),
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
        val query = exchange.request.uri.rawQuery
        return if (includeQueryString && !query.isNullOrBlank()) {
            "$path?$query"
        } else {
            path
        }
    }

    private fun traceId(exchange: ServerWebExchange): String =
        exchange.request.headers.getFirst(TRACE_ID_HEADER)
            ?: exchange.request.headers.getFirst(B3_TRACE_ID_HEADER)
            ?: UUID.randomUUID().toString()

    private fun remoteAddr(exchange: ServerWebExchange): String? =
        exchange.request.headers.getFirst("X-Forwarded-For")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: exchange.request.remoteAddress?.address?.hostAddress

    private fun elapsedMillis(startedNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos).coerceAtLeast(0)

    companion object {
        private const val TRACE_ID_HEADER = "X-Request-Id"
        private const val B3_TRACE_ID_HEADER = "X-B3-TraceId"
        private val log = LoggerFactory.getLogger(WebFluxApiAccessLoggingFilter::class.java)
    }
}
