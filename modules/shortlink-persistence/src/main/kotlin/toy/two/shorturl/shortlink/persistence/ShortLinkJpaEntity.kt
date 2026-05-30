package toy.two.shorturl.shortlink.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import toy.two.shorturl.shortlink.domain.OriginalUrl
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink
import java.time.Instant

@Entity
@Table(name = "short_links")
class ShortLinkJpaEntity(
    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 32)
    var code: String = "",

    @Column(name = "original_url", nullable = false, length = 2048)
    var originalUrl: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,
) {
    fun toDomain(): ShortLink =
        ShortLink(
            code = ShortCode.from(code),
            originalUrl = OriginalUrl.from(originalUrl),
            createdAt = createdAt,
            expiresAt = expiresAt,
            active = active,
        )

    companion object {
        fun from(shortLink: ShortLink): ShortLinkJpaEntity =
            ShortLinkJpaEntity(
                code = shortLink.code.value,
                originalUrl = shortLink.originalUrl.value,
                createdAt = shortLink.createdAt,
                expiresAt = shortLink.expiresAt,
                active = shortLink.active,
            )
    }
}
