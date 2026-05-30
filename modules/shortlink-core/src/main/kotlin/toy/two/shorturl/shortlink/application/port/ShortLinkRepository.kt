package toy.two.shorturl.shortlink.application.port

import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink

interface ShortLinkRepository {
    fun save(shortLink: ShortLink): ShortLink

    fun findByCode(code: ShortCode): ShortLink?

    fun existsByCode(code: ShortCode): Boolean

    fun findRecent(limit: Int): List<ShortLink>
}
