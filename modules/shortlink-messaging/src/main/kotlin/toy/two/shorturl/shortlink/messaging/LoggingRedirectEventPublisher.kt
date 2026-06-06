package toy.two.shorturl.shortlink.messaging

import org.slf4j.LoggerFactory
import toy.two.shorturl.shortlink.application.RedirectRecordedEvent
import toy.two.shorturl.shortlink.application.port.RedirectEventPublisher

class LoggingRedirectEventPublisher : RedirectEventPublisher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: RedirectRecordedEvent) {
        if (log.isDebugEnabled) {
            log.debug(
                "redirect event published code={} cacheHit={} redirectedAt={}",
                event.code,
                event.cacheHit,
                event.redirectedAt,
            )
        }
    }
}
