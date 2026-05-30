package toy.two.shorturl.shortlink.cache

import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.application.port.RedirectCacheEntry
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryShortLinkCache(
    private val clock: Clock,
) : ShortLinkCache {
    private val entries = ConcurrentHashMap<String, Entry>()

    override fun getRedirect(code: ShortCode): RedirectCacheEntry? {
        val entry = entries[code.value] ?: return null

        if (!entry.expiresAt.isAfter(clock.instant())) {
            entries.remove(code.value)
            return null
        }

        return entry.toCacheEntry()
    }

    override fun putFound(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration) {
        entries[code.value] = Entry(CacheValueType.FOUND, originalUrl.value, clock.instant().plus(ttl))
    }

    override fun putNotFound(code: ShortCode, ttl: Duration) {
        entries[code.value] = Entry(CacheValueType.NOT_FOUND, null, clock.instant().plus(ttl))
    }

    override fun putGone(code: ShortCode, ttl: Duration) {
        entries[code.value] = Entry(CacheValueType.GONE, null, clock.instant().plus(ttl))
    }

    override fun evict(code: ShortCode) {
        entries.remove(code.value)
    }

    private data class Entry(
        val type: CacheValueType,
        val originalUrl: String?,
        val expiresAt: Instant,
    ) {
        fun toCacheEntry(): RedirectCacheEntry =
            when (type) {
                CacheValueType.FOUND -> RedirectCacheEntry.Found(OriginalUrl.from(requireNotNull(originalUrl)))
                CacheValueType.NOT_FOUND -> RedirectCacheEntry.NotFound
                CacheValueType.GONE -> RedirectCacheEntry.Gone
            }
    }

    private enum class CacheValueType {
        FOUND,
        NOT_FOUND,
        GONE,
    }
}
