package toy.two.shorturl.shortlink.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import toy.two.shorturl.shortlink.application.RedirectCachePolicy
import toy.two.shorturl.shortlink.application.RedirectEventProcessor
import toy.two.shorturl.shortlink.application.RedirectResolver
import toy.two.shorturl.shortlink.application.ShortLinkCreator
import toy.two.shorturl.shortlink.application.ShortLinkReader
import toy.two.shorturl.shortlink.application.TsidShortCodeGenerator
import toy.two.shorturl.shortlink.application.port.RedirectEventPublisher
import toy.two.shorturl.shortlink.application.port.NoOpRedirectCacheLoadLock
import toy.two.shorturl.shortlink.application.port.NoOpRedirectMetricsRecorder
import toy.two.shorturl.shortlink.application.port.NoOpShortLinkCache
import toy.two.shorturl.shortlink.application.port.RedirectCacheLoadLock
import toy.two.shorturl.shortlink.application.port.RedirectMetricsRecorder
import toy.two.shorturl.shortlink.application.port.ShortCodeGenerator
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import java.time.Clock

@Configuration
@EnableConfigurationProperties(RedirectCacheProperties::class)
class ShortLinkCoreConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun redirectCachePolicy(properties: RedirectCacheProperties): RedirectCachePolicy =
        RedirectCachePolicy(
            defaultTtl = properties.defaultTtl,
            negativeTtl = properties.negativeTtl,
            goneTtl = properties.goneTtl,
            ttlJitterRatio = properties.ttlJitterRatio,
        )

    @Bean
    fun shortCodeGenerator(): ShortCodeGenerator = TsidShortCodeGenerator()

    @Bean
    @ConditionalOnMissingBean(RedirectMetricsRecorder::class)
    fun redirectMetricsRecorder(): RedirectMetricsRecorder = NoOpRedirectMetricsRecorder

    @Bean
    @ConditionalOnBean(ShortLinkRepository::class)
    fun shortLinkCreator(
        repository: ShortLinkRepository,
        codeGenerator: ShortCodeGenerator,
        clock: Clock,
        shortLinkCache: ObjectProvider<ShortLinkCache>,
    ): ShortLinkCreator = ShortLinkCreator(
        repository,
        codeGenerator,
        clock,
        shortLinkCache.getIfAvailable { NoOpShortLinkCache },
    )

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
        redirectCachePolicy: RedirectCachePolicy,
        redirectMetricsRecorder: RedirectMetricsRecorder,
        redirectCacheLoadLock: ObjectProvider<RedirectCacheLoadLock>,
    ): RedirectResolver = RedirectResolver(
        repository,
        cache,
        eventPublisher,
        clock,
        redirectCachePolicy,
        redirectMetricsRecorder,
        redirectCacheLoadLock.getIfAvailable { NoOpRedirectCacheLoadLock },
    )

    @Bean
    fun redirectEventProcessor(): RedirectEventProcessor = RedirectEventProcessor()
}
