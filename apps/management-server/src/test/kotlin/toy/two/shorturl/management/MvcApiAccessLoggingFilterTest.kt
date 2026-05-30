package toy.two.shorturl.management

import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.ObjectMapper
import toy.two.shorturl.common.logging.ApiAccessLogJsonWriter
import java.nio.file.Files
import kotlin.test.assertContains
import kotlin.test.assertFalse

class MvcApiAccessLoggingFilterTest {
    @Test
    fun `API 호출 정보를 JSON 라인 로그로 남긴다`() {
        val logFile = Files.createTempFile("management-api-access", ".log")
        val filter = MvcApiAccessLoggingFilter(
            applicationName = "short-url-management-server",
            writer = ApiAccessLogJsonWriter(ObjectMapper(), logFile),
        )
        val request = MockHttpServletRequest("POST", "/api/v1/short-links").apply {
            queryString = "debug=true"
            addHeader("User-Agent", "test-agent")
            addHeader("X-Request-Id", "trace-management-1")
            remoteAddr = "10.0.0.10"
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, servletResponse ->
            (servletResponse as HttpServletResponse).status = HttpServletResponse.SC_CREATED
        }

        val line = Files.readString(logFile).trim()
        assertContains(line, "\"application\":\"short-url-management-server\"")
        assertContains(line, "\"method\":\"POST\"")
        assertContains(line, "\"uri\":\"/api/v1/short-links?debug=true\"")
        assertContains(line, "\"status\":201")
        assertContains(line, "\"status_family\":\"2xx\"")
        assertContains(line, "\"trace_id\":\"trace-management-1\"")
        assertContains(line, "\"metric\":\"api_access\"")
        assertContains(line, "\"duration_ms\":")
    }

    @Test
    fun `actuator 요청은 access log에서 제외한다`() {
        val logFile = Files.createTempFile("management-actuator-access", ".log")
        val filter = MvcApiAccessLoggingFilter(
            applicationName = "short-url-management-server",
            writer = ApiAccessLogJsonWriter(ObjectMapper(), logFile),
        )
        val request = MockHttpServletRequest("GET", "/actuator/prometheus")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { _, servletResponse ->
            (servletResponse as HttpServletResponse).status = HttpServletResponse.SC_OK
        }

        assertFalse(Files.readString(logFile).contains("actuator"))
    }
}
