package toy.two.shorturl.redirect

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

class RedirectRequestGuardFilterTest {
    @Test
    fun `긴 redirect query는 controller 진입 전에 414로 거절한다`() {
        val filter = RedirectRequestGuardFilter(maxUriChars = 128, maxQueryChars = 32)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/go12?q=${"x".repeat(64)}").build(),
        )
        var chained = false
        val chain = WebFilterChain {
            chained = true
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertEquals(false, chained)
        assertEquals(HttpStatus.URI_TOO_LONG, exchange.response.statusCode)
    }

    @Test
    fun `허용 길이의 redirect 요청은 다음 filter로 넘긴다`() {
        val filter = RedirectRequestGuardFilter(maxUriChars = 128, maxQueryChars = 32)
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/go12?q=ok").build())
        var chained = false
        val chain = WebFilterChain {
            chained = true
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertEquals(true, chained)
    }
}
