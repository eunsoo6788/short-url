package toy.two.shorturl.shortlink.cache

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import java.time.Clock

@Configuration
class ShortLinkCacheConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "short-url.cache.redis", name = ["enabled"], havingValue = "true")
    fun redisShortLinkCache(redisTemplate: StringRedisTemplate): ShortLinkCache =
        RedisShortLinkCache(redisTemplate)

    @Bean
    @ConditionalOnMissingBean(ShortLinkCache::class)
    fun inMemoryShortLinkCache(clock: Clock): ShortLinkCache =
        InMemoryShortLinkCache(clock)
}
