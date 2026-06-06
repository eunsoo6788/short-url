package toy.two.shorturl.shortlink.application.port

import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Duration

interface ShortLinkCache {
    fun getRedirect(code: ShortCode): RedirectCacheEntry?

    fun putFound(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration)

    fun putNotFound(code: ShortCode, ttl: Duration)

    fun putGone(code: ShortCode, ttl: Duration)

    fun evict(code: ShortCode)
}

object NoOpShortLinkCache : ShortLinkCache {
    override fun getRedirect(code: ShortCode): RedirectCacheEntry? = null

    override fun putFound(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration) = Unit

    override fun putNotFound(code: ShortCode, ttl: Duration) = Unit

    override fun putGone(code: ShortCode, ttl: Duration) = Unit

    override fun evict(code: ShortCode) = Unit
}

sealed interface RedirectCacheEntry {
    data class Found(val originalUrl: OriginalUrl) : RedirectCacheEntry

    data object NotFound : RedirectCacheEntry

    data object Gone : RedirectCacheEntry
}
