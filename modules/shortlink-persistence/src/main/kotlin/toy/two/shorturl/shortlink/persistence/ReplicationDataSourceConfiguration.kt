package toy.two.shorturl.shortlink.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import javax.sql.DataSource

@Configuration
@EnableConfigurationProperties(DataSourceReplicationProperties::class)
@ConditionalOnProperty(
    prefix = "short-url.datasource.replication",
    name = ["enabled"],
    havingValue = "true",
)
class ReplicationDataSourceConfiguration {
    @Bean("primaryDataSource")
    fun primaryDataSource(environment: Environment): HikariDataSource =
        createDataSource(
            poolName = "short-url-primary",
            jdbcUrl = environment.getRequiredProperty("spring.datasource.url"),
            username = environment.getRequiredProperty("spring.datasource.username"),
            password = environment.getRequiredProperty("spring.datasource.password"),
            maximumPoolSize = environment.intProperty("spring.datasource.hikari.maximum-pool-size"),
            environment = environment,
        )

    @Bean("replicaDataSource")
    fun replicaDataSource(
        properties: DataSourceReplicationProperties,
        environment: Environment,
    ): HikariDataSource {
        val replicaUrl = properties.replica.url.takeIf { it.isNotBlank() }
            ?: error("short-url.datasource.replication.replica.url must be configured")

        return createDataSource(
            poolName = "short-url-replica",
            jdbcUrl = replicaUrl,
            username = properties.replica.username?.takeIf { it.isNotBlank() }
                ?: environment.getRequiredProperty("spring.datasource.username"),
            password = properties.replica.password?.takeIf { it.isNotBlank() }
                ?: environment.getRequiredProperty("spring.datasource.password"),
            maximumPoolSize = properties.replica.maximumPoolSize
                ?: environment.intProperty("spring.datasource.hikari.maximum-pool-size"),
            environment = environment,
        )
    }

    @Bean
    @Primary
    fun dataSource(
        @Qualifier("primaryDataSource") primaryDataSource: DataSource,
        @Qualifier("replicaDataSource") replicaDataSource: DataSource,
    ): DataSource =
        ReplicationRoutingDataSource().apply {
            setTargetDataSources(
                mapOf(
                    DataSourceLookupKey.PRIMARY to primaryDataSource,
                    DataSourceLookupKey.REPLICA to replicaDataSource,
                ),
            )
            setDefaultTargetDataSource(primaryDataSource)
            afterPropertiesSet()
        }

    private fun createDataSource(
        poolName: String,
        jdbcUrl: String,
        username: String,
        password: String,
        maximumPoolSize: Int?,
        environment: Environment,
    ): HikariDataSource {
        val config = HikariConfig()
        config.poolName = poolName
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        maximumPoolSize?.let { config.maximumPoolSize = it }
        environment.longProperty("spring.datasource.hikari.connection-timeout")
            ?.let { config.connectionTimeout = it }
        environment.longProperty("spring.datasource.hikari.validation-timeout")
            ?.let { config.validationTimeout = it }

        return HikariDataSource(config)
    }

    private fun Environment.longProperty(name: String): Long? =
        getProperty(name)?.toLongOrNull()

    private fun Environment.intProperty(name: String): Int? =
        getProperty(name)?.toIntOrNull()
}
