package toy.two.shorturl.shortlink.cache

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "short-url.cache.redis.replication")
class RedisReplicationProperties {
    var enabled: Boolean = false
    var master: RedisNodeProperties = RedisNodeProperties()
    var replicas: MutableList<RedisNodeProperties> = mutableListOf()
    var database: Int = 0
    var username: String? = null
    var password: String? = null
}

class RedisNodeProperties {
    var host: String = "localhost"
    var port: Int = 6379
}
