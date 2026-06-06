package toy.two.shorturl.shortlink.cache

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import toy.two.shorturl.shortlink.application.port.RedirectCacheEntry
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Duration
import java.util.concurrent.TimeUnit

class RedisShortLinkCache(
    private val redisTemplate: StringRedisTemplate,
) : TtlAwareShortLinkCache {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getRedirect(code: ShortCode): RedirectCacheEntry? =
        runCatching {
            redisTemplate.opsForValue().get(key(code))?.let { decode(it) }
        }.getOrElse { exception ->
            log.warn("redirect cache read failed code=${code.value}", exception)
            null
        }

    override fun getRedirectWithTtl(code: ShortCode): TtlAwareRedirectCacheEntry? =
        runCatching {
            val cacheKey = key(code)
            val value = redisTemplate.opsForValue().get(cacheKey) ?: return@runCatching null
            val entry = decode(value) ?: return@runCatching null
            val ttlMillis: Long? = redisTemplate.getExpire(cacheKey, TimeUnit.MILLISECONDS)
            val ttl = ttlMillis
                ?.takeIf { it > 0 }
                ?.let(Duration::ofMillis)

            TtlAwareRedirectCacheEntry(entry, ttl)
        }.getOrElse { exception ->
            log.warn("redirect cache read failed code=${code.value}", exception)
            null
        }

    override fun putFound(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration) {
        runCacheWrite("found", code) {
            redisTemplate.opsForValue().set(key(code), "$FOUND_PREFIX${originalUrl.value}", ttl)
        }
    }

    override fun putNotFound(code: ShortCode, ttl: Duration) {
        runCacheWrite("not_found", code) {
            redisTemplate.opsForValue().set(key(code), NOT_FOUND_VALUE, ttl)
        }
    }

    override fun putGone(code: ShortCode, ttl: Duration) {
        runCacheWrite("gone", code) {
            redisTemplate.opsForValue().set(key(code), GONE_VALUE, ttl)
        }
    }

    override fun evict(code: ShortCode) {
        runCacheWrite("evict", code) {
            redisTemplate.delete(key(code))
        }
    }

    private fun key(code: ShortCode): String = "short-url:redirect:${code.value}"

    private fun decode(value: String): RedirectCacheEntry? =
        when {
            value.startsWith(FOUND_PREFIX) -> RedirectCacheEntry.Found(
                OriginalUrl.from(value.removePrefix(FOUND_PREFIX)),
            )
            value == NOT_FOUND_VALUE -> RedirectCacheEntry.NotFound
            value == GONE_VALUE -> RedirectCacheEntry.Gone
            else -> null
        }

    private fun runCacheWrite(operation: String, code: ShortCode, block: () -> Unit) {
        runCatching(block)
            .onFailure { exception ->
                log.warn("redirect cache write failed operation=$operation code=${code.value}", exception)
            }
    }

    private companion object {
        private const val FOUND_PREFIX = "FOUND "
        private const val NOT_FOUND_VALUE = "NOT_FOUND"
        private const val GONE_VALUE = "GONE"
    }
}
