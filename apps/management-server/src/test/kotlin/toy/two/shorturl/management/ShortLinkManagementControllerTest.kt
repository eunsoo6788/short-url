package toy.two.shorturl.management

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import toy.two.shorturl.shortlink.application.CreateShortLinkCommand
import toy.two.shorturl.shortlink.application.ShortLinkCreator
import toy.two.shorturl.shortlink.application.ShortLinkReader
import toy.two.shorturl.shortlink.application.port.ShortCodeGenerator
import toy.two.shorturl.shortlink.application.port.ShortLinkRepository
import toy.two.shorturl.shortlink.domain.ShortCode
import toy.two.shorturl.shortlink.domain.ShortLink
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class ShortLinkManagementControllerTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `short link 생성 응답에 redirect 서버 URL을 포함한다`() {
        val repository = InMemoryShortLinkRepository()
        val controller = controller(repository)

        val response = controller.create(
            CreateShortLinkRequest(
                originalUrl = "https://example.com/post/1",
                customCode = "post1",
            ),
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("post1", response.body?.code)
        assertEquals("http://localhost:8081/post1", response.body?.shortUrl)
    }

    @Test
    fun `최근 생성한 short link 목록을 조회한다`() {
        val repository = InMemoryShortLinkRepository()
        val creator = ShortLinkCreator(repository, FixedShortCodeGenerator("auto1"), clock)
        creator.create(CreateShortLinkCommand("https://example.com/post/1", customCode = "post1"))
        val controller = ShortLinkManagementController(
            creator = creator,
            reader = ShortLinkReader(repository),
            publicBaseUrl = "http://localhost:8081",
        )

        val responses = controller.findRecent(limit = 10)

        assertEquals(1, responses.size)
        assertEquals("post1", responses.first().code)
    }

    private fun controller(repository: ShortLinkRepository): ShortLinkManagementController =
        ShortLinkManagementController(
            creator = ShortLinkCreator(repository, FixedShortCodeGenerator("auto1"), clock),
            reader = ShortLinkReader(repository),
            publicBaseUrl = "http://localhost:8081",
        )
}

private class FixedShortCodeGenerator(code: String) : ShortCodeGenerator {
    private val code = ShortCode.from(code)

    override fun generate(): ShortCode = code
}

private class InMemoryShortLinkRepository : ShortLinkRepository {
    private val store = linkedMapOf<String, ShortLink>()

    override fun save(shortLink: ShortLink): ShortLink {
        store[shortLink.code.value] = shortLink
        return shortLink
    }

    override fun findByCode(code: ShortCode): ShortLink? = store[code.value]

    override fun existsByCode(code: ShortCode): Boolean = store.containsKey(code.value)

    override fun findRecent(limit: Int): List<ShortLink> =
        store.values.sortedByDescending { it.createdAt }.take(limit)
}
