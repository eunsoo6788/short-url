package toy.two.shorturl.shortlink.application

import org.junit.jupiter.api.Test
import toy.two.shorturl.shortlink.application.port.RedirectCacheEntry
import toy.two.shorturl.shortlink.application.port.RedirectEventPublisher
import toy.two.shorturl.shortlink.application.port.RedirectMetricsRecorder
import toy.two.shorturl.shortlink.application.port.ShortCodeGenerator
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink
import toy.two.shorturl.shortlink.domain.exception.ExpiredShortLinkException
import toy.two.shorturl.shortlink.domain.exception.ShortCodeAlreadyExistsException
import toy.two.shorturl.shortlink.domain.exception.ShortLinkNotFoundException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    private val cachePolicy = RedirectCachePolicy(
        defaultTtl = Duration.ofMinutes(10),
        negativeTtl = Duration.ofSeconds(30),
        goneTtl = Duration.ofMinutes(1),
        ttlJitterRatio = 0.0,
    )

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
        val metrics = CapturingRedirectMetricsRecorder()
        val resolver = RedirectResolver(repository, cache, publisher, clock, cachePolicy, metrics)

        val resolution = resolver.resolve("miss")

        assertEquals("https://example.com/miss", resolution.originalUrl)
        assertEquals(false, resolution.cacheHit)
        assertEquals(
            "https://example.com/miss",
            (cache.getRedirect(ShortCode.from("miss")) as RedirectCacheEntry.Found).originalUrl.value,
        )
        assertEquals(1, publisher.events.size)
        assertEquals(1, metrics.cacheMiss)
        assertEquals(1, metrics.found)
        assertEquals(0, metrics.cacheHit)
    }

    @Test
    fun `cache hit면 repository 없이 redirect 대상 URL을 반환한다`() {
        val repository = InMemoryShortLinkRepository()
        val cache = InMemoryTestShortLinkCache()
        val publisher = CapturingRedirectEventPublisher()
        cache.putFound(ShortCode.from("hit1"), OriginalUrl.from("https://example.com/hit"), Duration.ofMinutes(1))
        val metrics = CapturingRedirectMetricsRecorder()
        val resolver = RedirectResolver(repository, cache, publisher, clock, cachePolicy, metrics)

        val resolution = resolver.resolve("hit1")

        assertEquals("https://example.com/hit", resolution.originalUrl)
        assertEquals(true, resolution.cacheHit)
        assertEquals(1, publisher.events.size)
        assertEquals(1, metrics.cacheHit)
        assertEquals(1, metrics.found)
        assertEquals(0, metrics.cacheMiss)
    }

    @Test
    fun `없는 code는 negative cache로 저장해 반복 DB 조회를 막는다`() {
        val repository = InMemoryShortLinkRepository()
        val cache = InMemoryTestShortLinkCache()
        val publisher = CapturingRedirectEventPublisher()
        val metrics = CapturingRedirectMetricsRecorder()
        val resolver = RedirectResolver(repository, cache, publisher, clock, cachePolicy, metrics)

        assertFailsWith<ShortLinkNotFoundException> {
            resolver.resolve("none")
        }
        assertFailsWith<ShortLinkNotFoundException> {
            resolver.resolve("none")
        }

        assertEquals(RedirectCacheEntry.NotFound, cache.getRedirect(ShortCode.from("none")))
        assertEquals(1, repository.findByCodeCount)
        assertEquals(Duration.ofSeconds(30), cache.lastTtl)
        assertEquals(0, publisher.events.size)
        assertEquals(1, metrics.cacheMiss)
        assertEquals(1, metrics.cacheHit)
        assertEquals(2, metrics.notFound)
    }

    @Test
    fun `만료된 short link는 gone cache로 저장한다`() {
        val repository = InMemoryShortLinkRepository()
        val cache = InMemoryTestShortLinkCache()
        val publisher = CapturingRedirectEventPublisher()
        repository.save(
            ShortLink(
                code = ShortCode.from("gone"),
                originalUrl = OriginalUrl.from("https://example.com/gone"),
                createdAt = clock.instant().minus(Duration.ofDays(2)),
                expiresAt = clock.instant().minus(Duration.ofDays(1)),
            ),
        )
        val metrics = CapturingRedirectMetricsRecorder()
        val resolver = RedirectResolver(repository, cache, publisher, clock, cachePolicy, metrics)

        assertFailsWith<ExpiredShortLinkException> {
            resolver.resolve("gone")
        }
        assertFailsWith<ExpiredShortLinkException> {
            resolver.resolve("gone")
        }

        assertEquals(RedirectCacheEntry.Gone, cache.getRedirect(ShortCode.from("gone")))
        assertEquals(1, repository.findByCodeCount)
        assertEquals(Duration.ofMinutes(1), cache.lastTtl)
        assertEquals(1, metrics.cacheMiss)
        assertEquals(1, metrics.cacheHit)
        assertEquals(2, metrics.gone)
    }

    @Test
    fun `만료 시간이 있는 short link는 기본 TTL과 남은 시간 중 더 짧은 값을 캐시한다`() {
        val repository = InMemoryShortLinkRepository()
        val cache = InMemoryTestShortLinkCache()
        val publisher = CapturingRedirectEventPublisher()
        repository.save(
            ShortLink(
                code = ShortCode.from("ttl1"),
                originalUrl = OriginalUrl.from("https://example.com/ttl"),
                createdAt = clock.instant(),
                expiresAt = clock.instant().plus(Duration.ofMinutes(2)),
            ),
        )
        val resolver = RedirectResolver(repository, cache, publisher, clock, cachePolicy)

        resolver.resolve("ttl1")

        assertEquals(Duration.ofMinutes(2), cache.lastTtl)
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
    var findByCodeCount = 0

    override fun save(shortLink: ShortLink): ShortLink {
        store[shortLink.code.value] = shortLink
        return shortLink
    }

    override fun findByCode(code: ShortCode): ShortLink? {
        findByCodeCount += 1
        return store[code.value]
    }

    override fun existsByCode(code: ShortCode): Boolean = store.containsKey(code.value)

    override fun findRecent(limit: Int): List<ShortLink> =
        store.values.sortedByDescending { it.createdAt }.take(limit)
}

private class InMemoryTestShortLinkCache : ShortLinkCache {
    private val store = mutableMapOf<String, RedirectCacheEntry>()
    var lastTtl: Duration? = null

    override fun getRedirect(code: ShortCode): RedirectCacheEntry? = store[code.value]

    override fun putFound(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration) {
        lastTtl = ttl
        store[code.value] = RedirectCacheEntry.Found(originalUrl)
    }

    override fun putNotFound(code: ShortCode, ttl: Duration) {
        lastTtl = ttl
        store[code.value] = RedirectCacheEntry.NotFound
    }

    override fun putGone(code: ShortCode, ttl: Duration) {
        lastTtl = ttl
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

private class CapturingRedirectMetricsRecorder : RedirectMetricsRecorder {
    var cacheHit = 0
    var cacheMiss = 0
    var found = 0
    var notFound = 0
    var gone = 0

    override fun recordCacheHit() {
        cacheHit += 1
    }

    override fun recordCacheMiss() {
        cacheMiss += 1
    }

    override fun recordFound() {
        found += 1
    }

    override fun recordNotFound() {
        notFound += 1
    }

    override fun recordGone() {
        gone += 1
    }
}
