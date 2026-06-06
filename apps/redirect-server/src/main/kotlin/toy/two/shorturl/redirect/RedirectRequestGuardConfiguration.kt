package toy.two.shorturl.redirect

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedirectRequestGuardConfiguration {
    @Bean
    fun redirectRequestGuardFilter(
        @Value("\${short-url.redirect.request.max-uri-chars:4096}") maxUriChars: Int,
        @Value("\${short-url.redirect.request.max-query-chars:2048}") maxQueryChars: Int,
    ): RedirectRequestGuardFilter =
        RedirectRequestGuardFilter(maxUriChars, maxQueryChars)
}
