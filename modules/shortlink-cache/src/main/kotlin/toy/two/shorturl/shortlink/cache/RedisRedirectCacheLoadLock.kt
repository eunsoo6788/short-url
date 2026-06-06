package toy.two.shorturl.shortlink.cache

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import toy.two.shorturl.shortlink.application.port.RedirectCacheEntry
import toy.two.shorturl.shortlink.application.port.RedirectCacheLoadLock
import toy.two.shorturl.shortlink.application.port.RedirectCacheLoadLockHandle
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

class RedisRedirectCacheLoadLock(
    private val redisTemplate: StringRedisTemplate,
    private val properties: CacheStampedeProperties,
) : RedirectCacheLoadLock {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun tryAcquire(code: ShortCode): RedirectCacheLoadLockHandle? {
        val lockKey = lockKey(code)
        val token = UUID.randomUUID().toString()
        val acquired = runCatching {
            redisTemplate.opsForValue().setIfAbsent(lockKey, token, properties.lockTtl) == true
        }.getOrElse { exception ->
            log.warn("redirect cache load lock acquire failed code=${code.value}", exception)
            return RedirectCacheLoadLockHandle {}
        }

        if (!acquired) {
            return null
        }

        return RedirectCacheLoadLockHandle {
            release(lockKey, token, code)
        }
    }

    override fun waitForCacheFill(
        code: ShortCode,
        cacheLookup: () -> RedirectCacheEntry?,
    ): RedirectCacheEntry? {
        val deadlineNanos = System.nanoTime() + properties.waitTimeout.toNanos()

        while (System.nanoTime() < deadlineNanos) {
            cacheLookup()?.let { return it }
            sleep(properties.pollInterval)
        }

        return cacheLookup()
    }

    private fun release(lockKey: String, token: String, code: ShortCode) {
        runCatching {
            redisTemplate.execute(RELEASE_SCRIPT, listOf(lockKey), token)
        }.onFailure { exception ->
            log.warn("redirect cache load lock release failed code=${code.value}", exception)
        }
    }

    private fun sleep(duration: Duration) {
        try {
            TimeUnit.NANOSECONDS.sleep(duration.toNanos())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun lockKey(code: ShortCode): String = "short-url:redirect:lock:${code.value}"

    private companion object {
        private val RELEASE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
