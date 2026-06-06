package toy.two.shorturl.redirect

import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

class RedirectRequestGuardFilter(
    private val maxUriChars: Int,
    private val maxQueryChars: Int,
) : WebFilter, Ordered {
    init {
        require(maxUriChars > 0) { "maxUriChars must be greater than 0" }
        require(maxQueryChars > 0) { "maxQueryChars must be greater than 0" }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange)
        }

        val query = exchange.request.uri.rawQuery
        val uriLength = path.length + if (query.isNullOrEmpty()) 0 else query.length + 1
        if (uriLength > maxUriChars || (query?.length ?: 0) > maxQueryChars) {
            return reject(exchange)
        }

        return chain.filter(exchange)
    }

    private fun reject(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        val body = URI_TOO_LONG_BODY.toByteArray(StandardCharsets.UTF_8)

        response.statusCode = HttpStatus.URI_TOO_LONG
        response.headers.contentType = MediaType.APPLICATION_JSON
        response.headers.contentLength = body.size.toLong()

        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)))
    }

    private companion object {
        private const val URI_TOO_LONG_BODY =
            """{"code":"URI_TOO_LONG","message":"Redirect request URI is too long"}"""
    }
}
