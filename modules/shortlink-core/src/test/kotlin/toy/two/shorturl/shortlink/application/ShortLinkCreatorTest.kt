package toy.two.shorturl.shortlink.application

import org.junit.jupiter.api.Test
import toy.two.shorturl.shortlink.application.port.RedirectEventPublisher
import toy.two.shorturl.shortlink.application.port.ShortCodeGenerator
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink
import toy.two.shorturl.shortlink.domain.exception.ShortCodeAlreadyExistsException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ShortLinkCreatorTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `custom code로 short link를 생성한다`() {
        val repository = InMemoryShortLinkRepository()
        val creator = ShortLinkCreator(repository, FixedShortCodeGenerator("abcd"), clock)

        val shortLink = creator.create(
            CreateShortLinkCommand(
                originalUrl = "https://example.com/articles/1",
                customCode = "news1",
            ),
        )

        assertEquals("news1", shortLink.code.value)
        assertEquals("https://example.com/articles/1", shortLink.originalUrl.value)
        assertEquals(shortLink, repository.findByCode(ShortCode.from("news1")))
    }

    @Test
    fun `이미 사용 중인 custom code는 거절한다`() {
        val repository = InMemoryShortLinkRepository()
        val creator = ShortLinkCreator(repository, FixedShortCodeGenerator("abcd"), clock)

        creator.create(CreateShortLinkCommand("https://example.com/1", customCode = "same"))

        assertFailsWith<ShortCodeAlreadyExistsException> {
            creator.create(CreateShortLinkCommand("https://example.com/2", customCode = "same"))
        }
    }

    @Test
    fun `custom code가 없으면 generator로 유니크 코드를 만든다`() {
        val repository = InMemoryShortLinkRepository()
        repository.save(
            ShortLink(
                code = ShortCode.from("dupe"),
                originalUrl = OriginalUrl.from("https://example.com/existing"),
                createdAt = clock.instant(),
            ),
        )
        val generator = SequenceShortCodeGenerator("dupe", "free")
        val creator = ShortLinkCreator(repository, generator, clock)

        val shortLink = creator.create(CreateShortLinkCommand("https://example.com/new"))

        assertEquals("free", shortLink.code.value)
        assertEquals(listOf("dupe", "free"), generator.generatedCodes.map { it.value })
    }
}

class RedirectResolverTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `cache miss면 repository에서 조회하고 cache에 저장한다`() {
        val repository = InMemoryShortLinkRepository()
        val cache = InMemoryTestShortLinkCache()
        val publisher = CapturingRedirectEventPublisher()
        repository.save(
            ShortLink(
                code = ShortCode.from("miss"),
                originalUrl = OriginalUrl.from("https://example.com/miss"),
                createdAt = clock.instant(),
            ),
        )
        val resolver = RedirectResolver(repository, cache, publisher, clock)

        val resolution = resolver.resolve("miss")

        assertEquals("https://example.com/miss", resolution.originalUrl)
        assertEquals(false, resolution.cacheHit)
        assertEquals("https://example.com/miss", cache.getOriginalUrl(ShortCode.from("miss"))?.value)
        assertEquals(1, publisher.events.size)
    }

    @Test
    fun `cache hit면 repository 없이 redirect 대상 URL을 반환한다`() {
        val repository = InMemoryShortLinkRepository()
        val cache = InMemoryTestShortLinkCache()
        val publisher = CapturingRedirectEventPublisher()
        cache.putOriginalUrl(ShortCode.from("hit1"), OriginalUrl.from("https://example.com/hit"), null)
        val resolver = RedirectResolver(repository, cache, publisher, clock)

        val resolution = resolver.resolve("hit1")

        assertEquals("https://example.com/hit", resolution.originalUrl)
        assertEquals(true, resolution.cacheHit)
        assertEquals(1, publisher.events.size)
    }
}

private class FixedShortCodeGenerator(code: String) : ShortCodeGenerator {
    private val code = ShortCode.from(code)

    override fun generate(): ShortCode = code
}

private class SequenceShortCodeGenerator(vararg codes: String) : ShortCodeGenerator {
    val remainingCodes = ArrayDeque(codes.map { ShortCode.from(it) })
    val generatedCodes = mutableListOf<ShortCode>()

    override fun generate(): ShortCode {
        val code = remainingCodes.removeFirst()
        generatedCodes += code
        return code
    }
}

private class InMemoryShortLinkRepository : ShortLinkRepository {
    private val store = linkedMapOf<String, ShortLink>()

    override fun save(shortLink: ShortLink): ShortLink {
        store[shortLink.code.value] = shortLink
        return shortLink
    }

    override fun findByCode(code: ShortCode): ShortLink? = store[code.value]

    override fun existsByCode(code: ShortCode): Boolean = store.containsKey(code.value)

    override fun findRecent(limit: Int): List<ShortLink> =
        store.values.sortedByDescending { it.createdAt }.take(limit)
}

private class InMemoryTestShortLinkCache : ShortLinkCache {
    private val store = mutableMapOf<String, OriginalUrl>()

    override fun getOriginalUrl(code: ShortCode): OriginalUrl? = store[code.value]

    override fun putOriginalUrl(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration?) {
        store[code.value] = originalUrl
    }
}

private class CapturingRedirectEventPublisher : RedirectEventPublisher {
    val events = mutableListOf<RedirectRecordedEvent>()

    override fun publish(event: RedirectRecordedEvent) {
        events += event
    }
}
