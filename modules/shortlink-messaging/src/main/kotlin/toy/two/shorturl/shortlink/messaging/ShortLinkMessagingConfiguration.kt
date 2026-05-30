package toy.two.shorturl.shortlink.messaging

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import toy.two.shorturl.shortlink.application.port.RedirectEventConsumer
import toy.two.shorturl.shortlink.application.port.RedirectEventPublisher

@Configuration
class ShortLinkMessagingConfiguration {
    @Bean
    @ConditionalOnMissingBean(RedirectEventPublisher::class)
    fun redirectEventPublisher(): RedirectEventPublisher =
        LoggingRedirectEventPublisher()

    @Bean
    @ConditionalOnMissingBean(RedirectEventConsumer::class)
    fun redirectEventConsumer(): RedirectEventConsumer =
        NoOpRedirectEventConsumer()
}
