package toy.two.shorturl.shortlink.application

import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Duration
import kotlin.math.max

data class RedirectCachePolicy(
    val defaultTtl: Duration = Duration.ofMinutes(10),
    val negativeTtl: Duration = Duration.ofSeconds(30),
    val goneTtl: Duration = Duration.ofMinutes(1),
    val ttlJitterRatio: Double = 0.1,
) {
    init {
        require(!defaultTtl.isNegative && !defaultTtl.isZero) {
            "defaultTtl은 0보다 커야 합니다."
        }
        require(!negativeTtl.isNegative && !negativeTtl.isZero) {
            "negativeTtl은 0보다 커야 합니다."
        }
        require(!goneTtl.isNegative && !goneTtl.isZero) {
            "goneTtl은 0보다 커야 합니다."
        }
        require(ttlJitterRatio in 0.0..0.5) {
            "ttlJitterRatio는 0.0 이상 0.5 이하만 허용합니다."
        }
    }

    fun foundTtl(code: ShortCode, remainingTtl: Duration?): Duration {
        val bounded = remainingTtl
            ?.takeIf { !it.isNegative && !it.isZero }
            ?.let { if (it < defaultTtl) it else defaultTtl }
            ?: defaultTtl

        return withJitter(code, bounded)
    }

    fun notFoundTtl(code: ShortCode): Duration =
        withJitter(code, negativeTtl)

    fun goneTtl(code: ShortCode): Duration =
        withJitter(code, goneTtl)

    private fun withJitter(code: ShortCode, ttl: Duration): Duration {
        val ttlMillis = ttl.toMillis()
        val maxJitterMillis = (ttlMillis * ttlJitterRatio).toLong()

        if (maxJitterMillis <= 0) {
            return ttl
        }

        val jitterMillis = Math.floorMod(code.value.hashCode().toLong(), maxJitterMillis + 1)
        val jitteredMillis = max(1, ttlMillis - jitterMillis)

        return Duration.ofMillis(jitteredMillis)
    }
}
