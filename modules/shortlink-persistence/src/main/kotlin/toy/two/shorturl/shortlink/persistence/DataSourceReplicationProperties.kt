package toy.two.shorturl.shortlink.persistence

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "short-url.datasource.replication")
class DataSourceReplicationProperties {
    var enabled: Boolean = false
    var replica: ReplicaDataSourceProperties = ReplicaDataSourceProperties()
}

class ReplicaDataSourceProperties {
    var url: String = ""
    var username: String? = null
    var password: String? = null
    var maximumPoolSize: Int? = null
}
