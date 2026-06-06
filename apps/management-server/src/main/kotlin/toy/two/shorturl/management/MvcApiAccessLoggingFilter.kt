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
                    path = truncate(request.requestURI, MAX_PATH_LENGTH) ?: "",
                    query = truncate(request.queryString, MAX_QUERY_LENGTH),
                    status = status,
                    statusFamily = statusFamily(status),
                    durationMs = elapsedMillis(startedNanos),
                    remoteAddr = truncate(remoteAddr(request), MAX_REMOTE_ADDR_LENGTH),
                    userAgent = truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH),
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
        val query = truncate(request.queryString, MAX_QUERY_LENGTH)
        val uri = if (includeQueryString && !query.isNullOrBlank()) {
            "${request.requestURI}?$query"
        } else {
            request.requestURI
        }

        return truncate(uri, MAX_URI_LENGTH) ?: ""
    }

    private fun traceId(request: HttpServletRequest): String =
        truncate(
            request.getHeader(TRACE_ID_HEADER)
            ?: request.getHeader(B3_TRACE_ID_HEADER)
            ?: UUID.randomUUID().toString(),
            MAX_TRACE_ID_LENGTH,
        ) ?: UUID.randomUUID().toString()

    private fun remoteAddr(request: HttpServletRequest): String? =
        request.getHeader("X-Forwarded-For")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: request.remoteAddr

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
        private val log = LoggerFactory.getLogger(MvcApiAccessLoggingFilter::class.java)
    }
}
