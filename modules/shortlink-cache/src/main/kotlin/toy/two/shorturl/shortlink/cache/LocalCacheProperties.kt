package toy.two.shorturl.shortlink.cache

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "short-url.cache.local")
data class LocalCacheProperties(
    var maximumSize: Long = 100_000,
) {
    init {
        require(maximumSize > 0) {
            "maximumSize는 0보다 커야 합니다."
        }
    }
}
