package toy.two.shorturl.shortlink.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import toy.two.shorturl.shortlink.application.RandomShortCodeGenerator
import toy.two.shorturl.shortlink.application.RedirectEventProcessor
import toy.two.shorturl.shortlink.application.RedirectResolver
import toy.two.shorturl.shortlink.application.ShortLinkCreator
import toy.two.shorturl.shortlink.application.ShortLinkReader
import toy.two.shorturl.shortlink.application.port.RedirectEventPublisher
import toy.two.shorturl.shortlink.application.port.ShortCodeGenerator
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import java.time.Clock

@Configuration
class ShortLinkCoreConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun shortCodeGenerator(): ShortCodeGenerator = RandomShortCodeGenerator()

    @Bean
    @ConditionalOnBean(ShortLinkRepository::class)
    fun shortLinkCreator(
        repository: ShortLinkRepository,
        codeGenerator: ShortCodeGenerator,
        clock: Clock,
    ): ShortLinkCreator = ShortLinkCreator(repository, codeGenerator, clock)

    @Bean
    @ConditionalOnBean(ShortLinkRepository::class)
    fun shortLinkReader(repository: ShortLinkRepository): ShortLinkReader =
        ShortLinkReader(repository)

    @Bean
    @ConditionalOnBean(ShortLinkRepository::class, ShortLinkCache::class, RedirectEventPublisher::class)
    fun redirectResolver(
        repository: ShortLinkRepository,
        cache: ShortLinkCache,
        eventPublisher: RedirectEventPublisher,
        clock: Clock,
    ): RedirectResolver = RedirectResolver(repository, cache, eventPublisher, clock)

    @Bean
    fun redirectEventProcessor(): RedirectEventProcessor = RedirectEventProcessor()
}
