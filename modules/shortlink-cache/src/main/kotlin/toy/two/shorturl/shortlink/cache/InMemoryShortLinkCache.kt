package toy.two.shorturl.shortlink.cache

import toy.two.shorturl.shortlink.application.port.ShortLinkCache
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

    override fun getOriginalUrl(code: ShortCode): OriginalUrl? {
        val entry = entries[code.value] ?: return null

        if (entry.expiresAt != null && !entry.expiresAt.isAfter(clock.instant())) {
            entries.remove(code.value)
            return null
        }

        return OriginalUrl.from(entry.originalUrl)
    }

    override fun putOriginalUrl(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration?) {
        val expiresAt = ttl?.let { clock.instant().plus(it) }
        entries[code.value] = Entry(originalUrl.value, expiresAt)
    }

    private data class Entry(
        val originalUrl: String,
        val expiresAt: Instant?,
    )
}
