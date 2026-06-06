package toy.two.shorturl.shortlink.persistence

import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.test.assertEquals

class ReplicationRoutingDataSourceTest {
    @Test
    fun `read only transaction은 replica datasource를 선택한다`() {
        val routingDataSource = ReplicationRoutingDataSource()

        assertEquals(DataSourceLookupKey.PRIMARY, routingDataSource.determineCurrentLookupKey())

        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true)
        try {
            assertEquals(DataSourceLookupKey.REPLICA, routingDataSource.determineCurrentLookupKey())
        } finally {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false)
        }
    }
}
