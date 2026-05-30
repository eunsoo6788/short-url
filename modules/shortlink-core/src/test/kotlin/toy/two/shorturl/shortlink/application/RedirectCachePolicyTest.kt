package toy.two.shorturl.shortlink.application

import org.junit.jupiter.api.Test
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedirectCachePolicyTest {
    @Test
    fun `만료 시간이 없으면 기본 TTL을 사용한다`() {
        val policy = RedirectCachePolicy(
            defaultTtl = Duration.ofMinutes(10),
            negativeTtl = Duration.ofSeconds(30),
            goneTtl = Duration.ofMinutes(1),
            ttlJitterRatio = 0.0,
        )

        assertEquals(Duration.ofMinutes(10), policy.foundTtl(ShortCode.from("code1"), null))
    }

    @Test
    fun `남은 만료 시간이 기본 TTL보다 짧으면 남은 시간을 사용한다`() {
        val policy = RedirectCachePolicy(
            defaultTtl = Duration.ofMinutes(10),
            negativeTtl = Duration.ofSeconds(30),
            goneTtl = Duration.ofMinutes(1),
            ttlJitterRatio = 0.0,
        )

        assertEquals(Duration.ofMinutes(2), policy.foundTtl(ShortCode.from("code1"), Duration.ofMinutes(2)))
    }

    @Test
    fun `TTL jitter는 원래 TTL보다 길게 만들지 않는다`() {
        val policy = RedirectCachePolicy(
            defaultTtl = Duration.ofMinutes(10),
            negativeTtl = Duration.ofSeconds(30),
            goneTtl = Duration.ofMinutes(1),
            ttlJitterRatio = 0.1,
        )

        val ttl = policy.foundTtl(ShortCode.from("code1"), null)

        assertTrue(ttl <= Duration.ofMinutes(10))
        assertTrue(ttl >= Duration.ofMinutes(9))
    }
}
