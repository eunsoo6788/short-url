package toy.two.shorturl.shortlink.persistence

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals

class ShortLinkMigrationTest {
    @Test
    fun `flyway migration으로 short_links 테이블을 생성한다`() {
        val jdbcUrl = "jdbc:h2:mem:shortlink_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"

        Flyway.configure()
            .dataSource(jdbcUrl, "sa", "")
            .locations("classpath:db/migration")
            .load()
            .migrate()

        DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
            connection.metaData.getTables(null, null, "short_links", arrayOf("TABLE")).use { resultSet ->
                resultSet.next()
                assertEquals("short_links", resultSet.getString("TABLE_NAME"))
            }
        }
    }
}
