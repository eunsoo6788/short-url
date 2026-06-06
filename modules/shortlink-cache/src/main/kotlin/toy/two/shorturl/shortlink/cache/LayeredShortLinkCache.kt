package toy.two.shorturl.shortlink.cache

import toy.two.shorturl.shortlink.application.port.RedirectCacheEntry
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class LayeredShortLinkCache(
    private val local: CaffeineShortLinkCache,
    private val remote: TtlAwareShortLinkCache,
) : ShortLinkCache {
    private val remoteLoadLocks = ConcurrentHashMap<String, Any>()

    override fun getRedirect(code: ShortCode): RedirectCacheEntry? {
        val localEntry = local.getRedirect(code)

        if (localEntry != null) {
            return localEntry
        }

        val lock = remoteLoadLocks.computeIfAbsent(code.value) { Any() }

        try {
            synchronized(lock) {
                val cachedAfterWait = local.getRedirect(code)

                if (cachedAfterWait != null) {
                    return cachedAfterWait
                }

                val remoteEntry = remote.getRedirectWithTtl(code) ?: return null
                val ttl = remoteEntry.ttl

                if (ttl != null && !ttl.isNegative && !ttl.isZero) {
                    local.put(code, remoteEntry.entry, ttl)
                }

                return remoteEntry.entry
            }
        } finally {
            remoteLoadLocks.remove(code.value, lock)
        }
    }

    override fun putFound(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration) {
        local.putFound(code, originalUrl, ttl)
        remote.putFound(code, originalUrl, ttl)
    }

    override fun putNotFound(code: ShortCode, ttl: Duration) {
        local.putNotFound(code, ttl)
        remote.putNotFound(code, ttl)
    }

    override fun putGone(code: ShortCode, ttl: Duration) {
        local.putGone(code, ttl)
        remote.putGone(code, ttl)
    }

    override fun evict(code: ShortCode) {
        local.evict(code)
        remote.evict(code)
    }
}

interface TtlAwareShortLinkCache : ShortLinkCache {
    fun getRedirectWithTtl(code: ShortCode): TtlAwareRedirectCacheEntry?
}

data class TtlAwareRedirectCacheEntry(
    val entry: RedirectCacheEntry,
    val ttl: Duration?,
)
