package toy.two.shorturl.redirect

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import toy.two.shorturl.shortlink.application.RedirectRecordedEvent
import toy.two.shorturl.shortlink.application.RedirectResolver
import toy.two.shorturl.shortlink.application.port.RedirectCacheEntry
import toy.two.shorturl.shortlink.application.port.RedirectEventPublisher
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class RedirectControllerTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `short code 요청을 원본 URL로 redirect한다`() {
        val repository = InMemoryShortLinkRepository()
        repository.save(
            ShortLink(
                code = ShortCode.from("go12"),
                originalUrl = OriginalUrl.from("https://example.com/go"),
                createdAt = clock.instant(),
            ),
        )
        val controller = RedirectController(
            RedirectResolver(
                repository = repository,
                cache = InMemoryTestShortLinkCache(),
                eventPublisher = CapturingRedirectEventPublisher(),
                clock = clock,
            ),
        )

        val response = controller.redirect("go12").block(Duration.ofSeconds(2))

        assertEquals(HttpStatus.FOUND, response?.statusCode)
        assertEquals(URI.create("https://example.com/go"), response?.headers?.location)
    }
}

private class InMemoryShortLinkRepository : ShortLinkRepository {
    private val store = mutableMapOf<String, ShortLink>()

    override fun save(shortLink: ShortLink): ShortLink {
        store[shortLink.code.value] = shortLink
        return shortLink
    }

    override fun findByCode(code: ShortCode): ShortLink? = store[code.value]

    override fun existsByCode(code: ShortCode): Boolean = store.containsKey(code.value)

    override fun findRecent(limit: Int): List<ShortLink> = store.values.take(limit)
}

private class InMemoryTestShortLinkCache : ShortLinkCache {
    private val store = mutableMapOf<String, RedirectCacheEntry>()

    override fun getRedirect(code: ShortCode): RedirectCacheEntry? = store[code.value]

    override fun putFound(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration) {
        store[code.value] = RedirectCacheEntry.Found(originalUrl)
    }

    override fun putNotFound(code: ShortCode, ttl: Duration) {
        store[code.value] = RedirectCacheEntry.NotFound
    }

    override fun putGone(code: ShortCode, ttl: Duration) {
        store[code.value] = RedirectCacheEntry.Gone
    }

    override fun evict(code: ShortCode) {
        store.remove(code.value)
    }
}

private class CapturingRedirectEventPublisher : RedirectEventPublisher {
    val events = mutableListOf<RedirectRecordedEvent>()

    override fun publish(event: RedirectRecordedEvent) {
        events += event
    }
}
