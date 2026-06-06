package toy.two.shorturl.shortlink.application

import toy.two.shorturl.shortlink.application.port.NoOpShortLinkCache
import toy.two.shorturl.shortlink.application.port.ShortCodeGenerator
import toy.two.shorturl.shortlink.application.port.ShortLinkCache
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink
import toy.two.shorturl.shortlink.domain.exception.ShortCodeAlreadyExistsException
import toy.two.shorturl.shortlink.domain.exception.ShortCodeGenerationFailedException
import java.time.Clock
import java.time.Instant

data class CreateShortLinkCommand(
    val originalUrl: String,
    val customCode: String? = null,
    val expiresAt: Instant? = null,
)

class ShortLinkCreator(
    private val repository: ShortLinkRepository,
    private val codeGenerator: ShortCodeGenerator,
    private val clock: Clock,
    private val redirectCache: ShortLinkCache = NoOpShortLinkCache,
) {
    fun create(command: CreateShortLinkCommand): ShortLink {
        val now = clock.instant()
        val code = command.customCode
            ?.takeIf { it.isNotBlank() }
            ?.let { ShortCode.from(it) }
            ?: generateUniqueCode()

        if (repository.existsByCode(code)) {
            throw ShortCodeAlreadyExistsException("이미 사용 중인 짧은 코드입니다: ${code.value}")
        }

        val shortLink = repository.save(
            ShortLink(
                code = code,
                originalUrl = OriginalUrl.from(command.originalUrl),
                createdAt = now,
                expiresAt = command.expiresAt,
            ),
        )

        redirectCache.evict(shortLink.code)
        return shortLink
    }

    private fun generateUniqueCode(): ShortCode {
        repeat(MAX_GENERATE_ATTEMPTS) {
            val code = codeGenerator.generate()

            if (!repository.existsByCode(code)) {
                return code
            }
        }

        throw ShortCodeGenerationFailedException("사용 가능한 짧은 코드를 생성하지 못했습니다.")
    }

    private companion object {
        private const val MAX_GENERATE_ATTEMPTS = 10
    }
}
