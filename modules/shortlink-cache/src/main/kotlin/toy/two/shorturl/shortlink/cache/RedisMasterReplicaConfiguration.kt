package toy.two.shorturl.shortlink.cache

import io.lettuce.core.ReadFrom
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

@Configuration
@EnableConfigurationProperties(RedisReplicationProperties::class)
@ConditionalOnProperty(
    prefix = "short-url.cache.redis.replication",
    name = ["enabled"],
    havingValue = "true",
)
class RedisMasterReplicaConfiguration {
    @Bean
    @Primary
    fun redisConnectionFactory(
        properties: RedisReplicationProperties,
        @Value("\${spring.data.redis.timeout:100ms}") timeout: Duration,
    ): RedisConnectionFactory {
        val serverConfiguration = RedisStaticMasterReplicaConfiguration(
            properties.master.host,
            properties.master.port,
        )

        properties.replicas.forEach { replica ->
            serverConfiguration.addNode(replica.host, replica.port)
        }
        serverConfiguration.setDatabase(properties.database)
        properties.username
            ?.takeIf { it.isNotBlank() }
            ?.let { serverConfiguration.setUsername(it) }
        properties.password
            ?.takeIf { it.isNotBlank() }
            ?.let { serverConfiguration.setPassword(RedisPassword.of(it)) }

        val clientConfiguration = LettuceClientConfiguration.builder()
            .commandTimeout(timeout)
            .readFrom(ReadFrom.REPLICA_PREFERRED)
            .build()

        return LettuceConnectionFactory(serverConfiguration, clientConfiguration)
    }

    @Bean
    @Primary
    fun stringRedisTemplate(redisConnectionFactory: RedisConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(redisConnectionFactory)
}
