package toy.two.shorturl.redirect

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import toy.two.shorturl.common.logging.ApiAccessLogJsonWriter
import java.nio.file.Files
import kotlin.test.assertContains
import kotlin.test.assertFalse

class WebFluxApiAccessLoggingFilterTest {
    @Test
    fun `API 호출 정보를 JSON 라인 로그로 남긴다`() {
        val logFile = Files.createTempFile("redirect-api-access", ".log")
        val filter = WebFluxApiAccessLoggingFilter(
            applicationName = "short-url-redirect-server",
            writer = ApiAccessLogJsonWriter(ObjectMapper(), logFile),
        )
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/go12?source=test")
                .header("User-Agent", "test-agent")
                .header("X-Request-Id", "trace-redirect-1")
                .build(),
        )
        val chain = WebFilterChain { filteredExchange ->
            filteredExchange.response.statusCode = HttpStatus.FOUND
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        val line = Files.readString(logFile).trim()
        assertContains(line, "\"application\":\"short-url-redirect-server\"")
        assertContains(line, "\"method\":\"GET\"")
        assertContains(line, "\"uri\":\"/go12?source=test\"")
        assertContains(line, "\"status\":302")
        assertContains(line, "\"status_family\":\"3xx\"")
        assertContains(line, "\"trace_id\":\"trace-redirect-1\"")
        assertContains(line, "\"metric\":\"api_access\"")
        assertContains(line, "\"duration_ms\":")
    }

    @Test
    fun `actuator 요청은 access log에서 제외한다`() {
        val logFile = Files.createTempFile("redirect-actuator-access", ".log")
        val filter = WebFluxApiAccessLoggingFilter(
            applicationName = "short-url-redirect-server",
            writer = ApiAccessLogJsonWriter(ObjectMapper(), logFile),
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/prometheus").build())
        val chain = WebFilterChain { filteredExchange ->
            filteredExchange.response.statusCode = HttpStatus.OK
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertFalse(Files.readString(logFile).contains("actuator"))
    }
}
