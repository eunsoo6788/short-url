package toy.two.shorturl.shortlink.persistence

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager

class ReplicationRoutingDataSource : AbstractRoutingDataSource() {
    public override fun determineCurrentLookupKey(): Any =
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            DataSourceLookupKey.REPLICA
        } else {
            DataSourceLookupKey.PRIMARY
        }
}

object DataSourceLookupKey {
    const val PRIMARY: String = "primary"
    const val REPLICA: String = "replica"
}
