package toy.two.shorturl.shortlink.domain

import toy.two.shorturl.shortlink.domain.exception.ExpiredShortLinkException
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class ShortLink(
    val code: ShortCode,
    val originalUrl: OriginalUrl,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val active: Boolean = true,
) {
    init {
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw ExpiredShortLinkException("만료 시각은 생성 시각보다 이후여야 합니다.")
        }
    }

    fun ensureRedirectable(clock: Clock) {
        if (!active || isExpired(clock)) {
            throw ExpiredShortLinkException("만료되었거나 비활성화된 짧은 URL입니다.")
        }
    }

    fun isExpired(clock: Clock): Boolean =
        expiresAt?.let { !it.isAfter(clock.instant()) } ?: false

    fun remainingTtl(clock: Clock): Duration? =
        expiresAt
            ?.let { Duration.between(clock.instant(), it) }
            ?.takeIf { !it.isNegative && !it.isZero }
}
