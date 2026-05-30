package toy.two.shorturl.shortlink.cache

import org.springframework.data.redis.core.StringRedisTemplate
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Duration

class RedisShortLinkCache(
    private val redisTemplate: StringRedisTemplate,
) : ShortLinkCache {
    override fun getOriginalUrl(code: ShortCode): OriginalUrl? =
        redisTemplate.opsForValue().get(key(code))?.let { OriginalUrl.from(it) }

    override fun putOriginalUrl(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration?) {
        if (ttl == null) {
            redisTemplate.opsForValue().set(key(code), originalUrl.value)
        } else {
            redisTemplate.opsForValue().set(key(code), originalUrl.value, ttl)
        }
    }

    private fun key(code: ShortCode): String = "short-url:redirect:${code.value}"
}
