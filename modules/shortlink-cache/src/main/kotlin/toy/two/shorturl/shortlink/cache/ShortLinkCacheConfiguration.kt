package toy.two.shorturl.shortlink.cache

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import toy.two.shorturl.shortlink.application.port.RedirectCacheLoadLock
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import java.time.Clock

@Configuration
@EnableConfigurationProperties(
    LocalCacheProperties::class,
    CacheStampedeProperties::class,
    RedisReplicationProperties::class,
)
class ShortLinkCacheConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "short-url.cache.redis", name = ["enabled"], havingValue = "true")
    fun redisShortLinkCache(redisTemplate: StringRedisTemplate): TtlAwareShortLinkCache =
        RedisShortLinkCache(redisTemplate)

    @Bean
    @ConditionalOnProperty(prefix = "short-url.cache.redis", name = ["enabled"], havingValue = "true")
    fun redisRedirectCacheLoadLock(
        redisTemplate: StringRedisTemplate,
        properties: CacheStampedeProperties,
    ): RedirectCacheLoadLock =
        RedisRedirectCacheLoadLock(redisTemplate, properties)

    @Bean
    fun caffeineShortLinkCache(
        clock: Clock,
        properties: LocalCacheProperties,
    ): CaffeineShortLinkCache =
        CaffeineShortLinkCache(clock, properties.maximumSize)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "short-url.cache.redis", name = ["enabled"], havingValue = "true")
    fun layeredShortLinkCache(
        local: CaffeineShortLinkCache,
        remote: TtlAwareShortLinkCache,
    ): ShortLinkCache =
        LayeredShortLinkCache(local, remote)
}
