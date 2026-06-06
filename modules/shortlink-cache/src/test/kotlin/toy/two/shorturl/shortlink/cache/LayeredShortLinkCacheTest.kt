package toy.two.shorturl.shortlink.cache

import org.junit.jupiter.api.Test
import toy.two.shorturl.shortlink.application.port.RedirectCacheEntry
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class LayeredShortLinkCacheTest {
    private val clock = MutableClock(Instant.parse("2026-06-01T00:00:00Z"))

    @Test
    fun `local cache hit이면 remote cache를 조회하지 않는다`() {
        val local = CaffeineShortLinkCache(clock, maximumSize = 100)
        val remote = CapturingRemoteShortLinkCache()
        val cache = LayeredShortLinkCache(local, remote)
        val code = ShortCode.from("local1")
        local.putFound(code, OriginalUrl.from("https://example.com/local"), Duration.ofMinutes(1))

        val entry = cache.getRedirect(code)

        assertEquals("https://example.com/local", (entry as RedirectCacheEntry.Found).originalUrl.value)
        assertEquals(0, remote.getCount)
    }

    @Test
    fun `local cache miss이고 remote cache hit이면 remote 값을 local cache에 채운다`() {
        val local = CaffeineShortLinkCache(clock, maximumSize = 100)
        val remote = CapturingRemoteShortLinkCache()
        val cache = LayeredShortLinkCache(local, remote)
        val code = ShortCode.from("remote1")
        remote.putFound(code, OriginalUrl.from("https://example.com/remote"), Duration.ofMinutes(1))

        val first = cache.getRedirect(code)
        val second = cache.getRedirect(code)

        assertEquals("https://example.com/remote", (first as RedirectCacheEntry.Found).originalUrl.value)
        assertEquals("https://example.com/remote", (second as RedirectCacheEntry.Found).originalUrl.value)
        assertEquals(1, remote.getCount)
    }

    @Test
    fun `같은 JVM에서 local cache miss가 동시에 발생해도 remote cache는 한 번만 조회한다`() {
        val local = CaffeineShortLinkCache(clock, maximumSize = 100)
        val remote = CapturingRemoteShortLinkCache(delay = Duration.ofMillis(50))
        val cache = LayeredShortLinkCache(local, remote)
        val code = ShortCode.from("hot1")
        remote.putFound(code, OriginalUrl.from("https://example.com/hot"), Duration.ofMinutes(1))

        val threads = List(10) {
            thread {
                cache.getRedirect(code)
            }
        }

        threads.forEach { it.join() }

        assertEquals(1, remote.getCount)
    }

    @Test
    fun `cache write는 local과 remote에 모두 저장한다`() {
        val local = CaffeineShortLinkCache(clock, maximumSize = 100)
        val remote = CapturingRemoteShortLinkCache()
        val cache = LayeredShortLinkCache(local, remote)
        val code = ShortCode.from("write1")

        cache.putGone(code, Duration.ofMinutes(1))

        assertEquals(RedirectCacheEntry.Gone, local.getRedirect(code))
        assertEquals(RedirectCacheEntry.Gone, remote.getRedirect(code))
    }

    @Test
    fun `evict는 local과 remote에서 모두 삭제한다`() {
        val local = CaffeineShortLinkCache(clock, maximumSize = 100)
        val remote = CapturingRemoteShortLinkCache()
        val cache = LayeredShortLinkCache(local, remote)
        val code = ShortCode.from("evict1")
        cache.putNotFound(code, Duration.ofMinutes(1))

        cache.evict(code)

        assertEquals(null, local.getRedirect(code))
        assertEquals(null, remote.getRedirect(code))
    }
}

private class CapturingRemoteShortLinkCache(
    private val delay: Duration = Duration.ZERO,
) : TtlAwareShortLinkCache {
    private val entries = mutableMapOf<String, Entry>()
    private val remoteGetCount = AtomicInteger(0)
    val getCount: Int
        get() = remoteGetCount.get()

    override fun getRedirectWithTtl(code: ShortCode): TtlAwareRedirectCacheEntry? {
        remoteGetCount.incrementAndGet()
        if (!delay.isZero) {
            Thread.sleep(delay.toMillis())
        }
        val entry = entries[code.value] ?: return null

        return TtlAwareRedirectCacheEntry(entry.value, entry.ttl)
    }

    override fun getRedirect(code: ShortCode): RedirectCacheEntry? =
        entries[code.value]?.value

    override fun putFound(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration) {
        entries[code.value] = Entry(RedirectCacheEntry.Found(originalUrl), ttl)
    }

    override fun putNotFound(code: ShortCode, ttl: Duration) {
        entries[code.value] = Entry(RedirectCacheEntry.NotFound, ttl)
    }

    override fun putGone(code: ShortCode, ttl: Duration) {
        entries[code.value] = Entry(RedirectCacheEntry.Gone, ttl)
    }

    override fun evict(code: ShortCode) {
        entries.remove(code.value)
    }

    private data class Entry(
        val value: RedirectCacheEntry,
        val ttl: Duration,
    )
}

private class MutableClock(
    private val instant: Instant,
    private val zone: ZoneId = ZoneId.of("UTC"),
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock =
        MutableClock(instant, zone)

    override fun instant(): Instant = instant
}
