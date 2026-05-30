package toy.two.shorturl.shortlink.application

import toy.two.shorturl.shortlink.application.port.RedirectEventPublisher
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.exception.ShortLinkNotFoundException
import java.time.Clock
import java.time.Instant

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
) {
    fun resolve(codeValue: String): RedirectResolution {
        val code = ShortCode.from(codeValue)
        val cachedUrl = cache.getOriginalUrl(code)

        if (cachedUrl != null) {
            return resolved(code, cachedUrl, cacheHit = true)
        }

        val shortLink = repository.findByCode(code)
            ?: throw ShortLinkNotFoundException("짧은 URL을 찾을 수 없습니다: ${code.value}")

        shortLink.ensureRedirectable(clock)
        cache.putOriginalUrl(code, shortLink.originalUrl, shortLink.remainingTtl(clock))

        return resolved(code, shortLink.originalUrl, cacheHit = false)
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
