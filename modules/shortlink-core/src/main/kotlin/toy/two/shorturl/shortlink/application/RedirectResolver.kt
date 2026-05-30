package toy.two.shorturl.shortlink.application

import toy.two.shorturl.shortlink.application.port.RedirectEventPublisher
import toy.two.shorturl.shortlink.application.port.RedirectCacheEntry
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink
import toy.two.shorturl.shortlink.domain.exception.ExpiredShortLinkException
import toy.two.shorturl.shortlink.domain.exception.ShortLinkNotFoundException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class RedirectResolution(
    val code: String,
    val originalUrl: String,
    val cacheHit: Boolean,
)

data class RedirectRecordedEvent(
    val code: String,
    val originalUrl: String,
    val redirectedAt: Instant,
    val cacheHit: Boolean,
)

class RedirectResolver(
    private val repository: ShortLinkRepository,
    private val cache: ShortLinkCache,
    private val eventPublisher: RedirectEventPublisher,
    private val clock: Clock,
    private val cachePolicy: RedirectCachePolicy = RedirectCachePolicy(),
) {
    private val loadLocks = ConcurrentHashMap<String, Any>()

    fun resolve(codeValue: String): RedirectResolution {
        val code = ShortCode.from(codeValue)

        return when (val cached = cache.getRedirect(code)) {
            is RedirectCacheEntry.Found -> resolved(code, cached.originalUrl, cacheHit = true)
            RedirectCacheEntry.NotFound -> throw ShortLinkNotFoundException("짧은 URL을 찾을 수 없습니다: ${code.value}")
            RedirectCacheEntry.Gone -> throw ExpiredShortLinkException("만료되었거나 비활성화된 짧은 URL입니다.")
            null -> resolveWithSingleFlight(code)
        }
    }

    private fun resolveWithSingleFlight(code: ShortCode): RedirectResolution {
        val lock = loadLocks.computeIfAbsent(code.value) { Any() }

        try {
            synchronized(lock) {
                return when (val cached = cache.getRedirect(code)) {
                    is RedirectCacheEntry.Found -> resolved(code, cached.originalUrl, cacheHit = true)
                    RedirectCacheEntry.NotFound -> throw ShortLinkNotFoundException(
                        "짧은 URL을 찾을 수 없습니다: ${code.value}",
                    )
                    RedirectCacheEntry.Gone -> throw ExpiredShortLinkException(
                        "만료되었거나 비활성화된 짧은 URL입니다.",
                    )
                    null -> resolveFromRepository(code)
                }
            }
        } finally {
            loadLocks.remove(code.value, lock)
        }
    }

    private fun resolveFromRepository(code: ShortCode): RedirectResolution {
        val shortLink = repository.findByCode(code)
            ?: return cacheNotFoundAndThrow(code)

        if (!shortLink.active || shortLink.isExpired(clock)) {
            cache.putGone(code, cachePolicy.goneTtl(code))
            shortLink.ensureRedirectable(clock)
        }

        cacheFound(code, shortLink)
        return resolved(code, shortLink.originalUrl, cacheHit = false)
    }

    private fun cacheFound(code: ShortCode, shortLink: ShortLink) {
        cache.putFound(
            code = code,
            originalUrl = shortLink.originalUrl,
            ttl = cachePolicy.foundTtl(code, shortLink.remainingTtl(clock)),
        )
    }

    private fun cacheNotFoundAndThrow(code: ShortCode): Nothing {
        cache.putNotFound(code, cachePolicy.notFoundTtl(code))
        throw ShortLinkNotFoundException("짧은 URL을 찾을 수 없습니다: ${code.value}")
    }

    private fun resolved(code: ShortCode, originalUrl: OriginalUrl, cacheHit: Boolean): RedirectResolution {
        eventPublisher.publish(
            RedirectRecordedEvent(
                code = code.value,
                originalUrl = originalUrl.value,
                redirectedAt = clock.instant(),
                cacheHit = cacheHit,
            ),
        )

        return RedirectResolution(
            code = code.value,
            originalUrl = originalUrl.value,
            cacheHit = cacheHit,
        )
    }
}
