package toy.two.shorturl.shortlink.application.port

import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import java.time.Duration

interface ShortLinkCache {
    fun getOriginalUrl(code: ShortCode): OriginalUrl?

    fun putOriginalUrl(code: ShortCode, originalUrl: OriginalUrl, ttl: Duration?)
}
