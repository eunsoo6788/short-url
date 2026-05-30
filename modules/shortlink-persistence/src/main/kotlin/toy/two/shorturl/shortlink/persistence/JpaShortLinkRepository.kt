package toy.two.shorturl.shortlink.persistence

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink

@Repository
class JpaShortLinkRepository(
    private val jpaRepository: ShortLinkJpaRepository,
) : ShortLinkRepository {
    @Transactional
    override fun save(shortLink: ShortLink): ShortLink =
        jpaRepository.save(ShortLinkJpaEntity.from(shortLink)).toDomain()

    @Transactional(readOnly = true)
    override fun findByCode(code: ShortCode): ShortLink? =
        jpaRepository.findById(code.value)
            .map { it.toDomain() }
            .orElse(null)

    @Transactional(readOnly = true)
    override fun existsByCode(code: ShortCode): Boolean =
        jpaRepository.existsById(code.value)

    @Transactional(readOnly = true)
    override fun findRecent(limit: Int): List<ShortLink> =
        jpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit.coerceIn(1, 100)))
            .map { it.toDomain() }
}
