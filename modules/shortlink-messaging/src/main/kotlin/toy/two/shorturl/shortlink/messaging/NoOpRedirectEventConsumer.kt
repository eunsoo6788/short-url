package toy.two.shorturl.shortlink.messaging

import toy.two.shorturl.shortlink.application.RedirectRecordedEvent
import toy.two.shorturl.shortlink.application.port.RedirectEventConsumer

class NoOpRedirectEventConsumer : RedirectEventConsumer {
    override fun poll(maxMessages: Int): List<RedirectRecordedEvent> = emptyList()
}
