package toy.two.shorturl.shortlink.application.port

import toy.two.shorturl.shortlink.domain.ShortCode

interface ShortCodeGenerator {
    fun generate(): ShortCode
}
