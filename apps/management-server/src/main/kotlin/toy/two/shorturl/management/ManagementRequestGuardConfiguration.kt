package toy.two.shorturl.management

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
class ManagementRequestGuardConfiguration {
    @Bean
    fun createShortLinkRequestSizeFilter(
        @Value("\${short-url.management.create.max-request-body-bytes:4096}") maxBodyBytes: Long,
        @Value("\${short-url.management.create.max-drain-body-bytes:0}") maxDrainBodyBytes: Long,
    ): FilterRegistrationBean<CreateShortLinkRequestSizeFilter> =
        FilterRegistrationBean(CreateShortLinkRequestSizeFilter(maxBodyBytes, maxDrainBodyBytes)).apply {
            addUrlPatterns("/*")
            order = Ordered.HIGHEST_PRECEDENCE + 1
        }
}
