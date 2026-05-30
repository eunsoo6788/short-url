package toy.two.shorturl.shortlink.persistence

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EntityScan(basePackageClasses = [ShortLinkJpaEntity::class])
@EnableJpaRepositories(basePackageClasses = [ShortLinkJpaRepository::class])
class ShortLinkPersistenceConfiguration
