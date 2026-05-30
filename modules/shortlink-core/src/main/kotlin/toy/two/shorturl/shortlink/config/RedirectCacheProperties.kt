package toy.two.shorturl.shortlink.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "short-url.cache.redirect")
data class RedirectCacheProperties(
    var defaultTtl: Duration = Duration.ofMinutes(10),
    var negativeTtl: Duration = Duration.ofSeconds(30),
    var goneTtl: Duration = Duration.ofMinutes(1),
    var ttlJitterRatio: Double = 0.1,
)
