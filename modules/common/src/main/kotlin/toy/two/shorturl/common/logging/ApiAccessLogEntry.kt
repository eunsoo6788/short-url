package toy.two.shorturl.common.logging

import java.time.Instant

data class ApiAccessLogEntry(
    val timestamp: Instant,
    val application: String,
    val method: String,
    val uri: String,
    val path: String,
    val query: String?,
    val status: Int,
    val statusFamily: String,
    val durationMs: Long,
    val remoteAddr: String?,
    val userAgent: String?,
    val traceId: String,
    val metric: String = "api_access",
) {
    fun toJsonMap(): Map<String, Any?> =
        linkedMapOf(
            "timestamp" to timestamp.toString(),
            "application" to application,
            "method" to method,
            "uri" to uri,
            "path" to path,
            "query" to query,
            "status" to status,
            "status_family" to statusFamily,
            "duration_ms" to durationMs,
            "remote_addr" to remoteAddr,
            "user_agent" to userAgent,
            "trace_id" to traceId,
            "metric" to metric,
            "metrics" to mapOf(
                "duration_ms" to durationMs,
                "status" to status,
            ),
        )
}

fun statusFamily(status: Int): String =
    when (status) {
        in 100..199 -> "1xx"
        in 200..299 -> "2xx"
        in 300..399 -> "3xx"
        in 400..499 -> "4xx"
        in 500..599 -> "5xx"
        else -> "unknown"
    }
