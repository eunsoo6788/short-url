package toy.two.shorturl.shortlink.cache

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "short-url.cache.stampede")
data class CacheStampedeProperties(
    var lockTtl: Duration = Duration.ofSeconds(3),
    var waitTimeout: Duration = Duration.ofMillis(500),
    var pollInterval: Duration = Duration.ofMillis(20),
) {
    init {
        require(!lockTtl.isNegative && !lockTtl.isZero) {
            "lockTtl은 0보다 커야 합니다."
        }
        require(!waitTimeout.isNegative) {
            "waitTimeout은 0 이상이어야 합니다."
        }
        require(!pollInterval.isNegative && !pollInterval.isZero) {
            "pollInterval은 0보다 커야 합니다."
        }
    }
}
