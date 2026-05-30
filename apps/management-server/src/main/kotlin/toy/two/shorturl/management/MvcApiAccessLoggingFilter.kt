package toy.two.shorturl.management

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import toy.two.shorturl.common.logging.ApiAccessLogEntry
import toy.two.shorturl.common.logging.ApiAccessLogJsonWriter
import toy.two.shorturl.common.logging.statusFamily
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

class MvcApiAccessLoggingFilter(
    private val applicationName: String,
    private val writer: ApiAccessLogJsonWriter,
    private val includeQueryString: Boolean = true,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/actuator")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = Instant.now()
        val startedNanos = System.nanoTime()
        val traceId = traceId(request)
        var failure: Throwable? = null

        response.setHeader(TRACE_ID_HEADER, traceId)

        try {
            filterChain.doFilter(request, response)
        } catch (ex: Throwable) {
            failure = ex
            throw ex
        } finally {
            val status = if (failure != null && response.status < 400) {
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            } else {
                response.status
            }

            writeSafely(
                ApiAccessLogEntry(
                    timestamp = startedAt,
                    application = applicationName,
                    method = request.method,
                    uri = requestUri(request),
                    path = request.requestURI,
                    query = request.queryString,
                    status = status,
                    statusFamily = statusFamily(status),
                    durationMs = elapsedMillis(startedNanos),
                    remoteAddr = remoteAddr(request),
                    userAgent = request.getHeader("User-Agent"),
                    traceId = traceId,
                ),
            )
        }
    }

    private fun writeSafely(entry: ApiAccessLogEntry) {
        runCatching { writer.write(entry) }
            .onFailure { log.warn("Failed to write API access log", it) }
    }

    private fun requestUri(request: HttpServletRequest): String {
        val query = request.queryString
        return if (includeQueryString && !query.isNullOrBlank()) {
            "${request.requestURI}?$query"
        } else {
            request.requestURI
        }
    }

    private fun traceId(request: HttpServletRequest): String =
        request.getHeader(TRACE_ID_HEADER)
            ?: request.getHeader(B3_TRACE_ID_HEADER)
            ?: UUID.randomUUID().toString()

    private fun remoteAddr(request: HttpServletRequest): String? =
        request.getHeader("X-Forwarded-For")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: request.remoteAddr

    private fun elapsedMillis(startedNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos).coerceAtLeast(0)

    companion object {
        private const val TRACE_ID_HEADER = "X-Request-Id"
        private const val B3_TRACE_ID_HEADER = "X-B3-TraceId"
        private val log = LoggerFactory.getLogger(MvcApiAccessLoggingFilter::class.java)
    }
}
