package toy.two.shorturl.shortlink.application

import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink
import toy.two.shorturl.shortlink.domain.exception.ShortLinkNotFoundException

class ShortLinkReader(
    private val repository: ShortLinkRepository,
) {
    fun get(codeValue: String): ShortLink {
        val code = ShortCode.from(codeValue)

        return repository.findByCode(code)
            ?: throw ShortLinkNotFoundException("짧은 URL을 찾을 수 없습니다: ${code.value}")
    }

    fun findRecent(limit: Int): List<ShortLink> =
        repository.findRecent(limit.coerceIn(1, 100))
}
