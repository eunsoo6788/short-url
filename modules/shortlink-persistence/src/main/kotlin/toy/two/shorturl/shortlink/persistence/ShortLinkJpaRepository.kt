package toy.two.shorturl.shortlink.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ShortLinkJpaRepository : JpaRepository<ShortLinkJpaEntity, String> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<ShortLinkJpaEntity>
}
