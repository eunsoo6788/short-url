package toy.two.shorturl.worker

import org.junit.jupiter.api.Test
import toy.two.shorturl.shortlink.application.RedirectEventProcessor
import toy.two.shorturl.shortlink.application.RedirectRecordedEvent
import toy.two.shorturl.shortlink.application.port.RedirectEventConsumer
import java.time.Instant
import kotlin.test.assertEquals

class RedirectEventPollingWorkerTest {
    @Test
    fun `polling worker가 consumer에서 이벤트를 가져온다`() {
        val consumer = CapturingRedirectEventConsumer()
        val worker = RedirectEventPollingWorker(
            consumer = consumer,
            processor = RedirectEventProcessor(),
            maxMessages = 10,
        )

        worker.poll()

        assertEquals(10, consumer.lastMaxMessages)
    }
}

private class CapturingRedirectEventConsumer : RedirectEventConsumer {
    var lastMaxMessages: Int = 0

    override fun poll(maxMessages: Int): List<RedirectRecordedEvent> {
        lastMaxMessages = maxMessages
        return listOf(
            RedirectRecordedEvent(
                code = "go12",
                originalUrl = "https://example.com/go",
                redirectedAt = Instant.parse("2026-05-31T00:00:00Z"),
                cacheHit = false,
            ),
        )
    }
}
