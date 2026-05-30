package toy.two.shorturl.shortlink.domain

import toy.two.shorturl.shortlink.domain.exception.InvalidOriginalUrlException
import java.net.URI

@JvmInline
value class OriginalUrl private constructor(val value: String) {
    companion object {
        fun from(value: String): OriginalUrl {
            val normalized = value.trim()
            val uri = runCatching { URI(normalized) }
                .getOrElse { throw InvalidOriginalUrlException("URL 형식이 올바르지 않습니다.") }

            val scheme = uri.scheme?.lowercase()

            if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                throw InvalidOriginalUrlException("http 또는 https URL만 사용할 수 있습니다.")
            }

            if (normalized.length > 2048) {
                throw InvalidOriginalUrlException("URL은 2048자를 넘을 수 없습니다.")
            }

            return OriginalUrl(normalized)
        }
    }
}
