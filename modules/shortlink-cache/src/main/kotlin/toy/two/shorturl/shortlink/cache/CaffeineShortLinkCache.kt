package toy.two.shorturl.shortlink.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import toy.two.shorturl.shortlink.application.port.RedirectCacheEntry
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Clock
import java.time.Duration
import java.time.Instant

class CaffeineShortLinkCache(
    private val clock: Clock,
    maximumSize: Long,
) : ShortLinkCache {
    private val entries = Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .expireAfter(
            object : Expiry<String, Entry> {
                override fun expireAfterCreate(key: String, value: Entry, currentTime: Long): Long =
                    value.expiresAfterNanos(clock)

                override fun expireAfterUpdate(
                    key: String,
                    value: Entry,
                    currentTime: Long,
                    currentDuration: Long,
                ): Long = value.expiresAfterNanos(clock)

                override fun expireAfterRead(
                    key: String,
                    value: Entry,
                    currentTime: Long,
                    currentDuration: Long,
                ): Long = currentDuration
            },
        )
        .build<String, Entry>()

    override fun getRedirect(code: ShortCode): RedirectCacheEntry? {
        val entry = entries.getIfPresent(code.value) ?: return null

        if (entry.isExpired(clock)) {
            entries.invalidate(code.value)
            return null
        }

        return entry.toCacheEntry()
    }

    override fun putFound(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration) {
        put(code, RedirectCacheEntry.Found(originalUrl), ttl)
    }

    override fun putNotFound(code: ShortCode, ttl: Duration) {
        put(code, RedirectCacheEntry.NotFound, ttl)
    }

    override fun putGone(code: ShortCode, ttl: Duration) {
        put(code, RedirectCacheEntry.Gone, ttl)
    }

    override fun evict(code: ShortCode) {
        entries.invalidate(code.value)
    }

    fun put(code: ShortCode, entry: RedirectCacheEntry, ttl: Duration) {
        if (ttl.isNegative || ttl.isZero) {
            entries.invalidate(code.value)
            return
        }

        entries.put(code.value, Entry.from(entry, clock.instant().plus(ttl)))
    }

    private data class Entry(
        val type: CacheValueType,
        val originalUrl: String?,
        val expiresAt: Instant,
    ) {
        fun isExpired(clock: Clock): Boolean =
            !expiresAt.isAfter(clock.instant())

        fun expiresAfterNanos(clock: Clock): Long =
            Duration.between(clock.instant(), expiresAt)
                .takeIf { !it.isNegative && !it.isZero }
                ?.toNanos()
                ?: 1L

        fun toCacheEntry(): RedirectCacheEntry =
            when (type) {
                CacheValueType.FOUND -> RedirectCacheEntry.Found(OriginalUrl.from(requireNotNull(originalUrl)))
                CacheValueType.NOT_FOUND -> RedirectCacheEntry.NotFound
                CacheValueType.GONE -> RedirectCacheEntry.Gone
            }

        companion object {
            fun from(entry: RedirectCacheEntry, expiresAt: Instant): Entry =
                when (entry) {
                    is RedirectCacheEntry.Found -> Entry(CacheValueType.FOUND, entry.originalUrl.value, expiresAt)
                    RedirectCacheEntry.NotFound -> Entry(CacheValueType.NOT_FOUND, null, expiresAt)
                    RedirectCacheEntry.Gone -> Entry(CacheValueType.GONE, null, expiresAt)
                }
        }
    }

    private enum class CacheValueType {
        FOUND,
        NOT_FOUND,
        GONE,
    }
}
