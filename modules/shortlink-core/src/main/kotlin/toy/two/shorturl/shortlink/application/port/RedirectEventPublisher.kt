package toy.two.shorturl.shortlink.application.port

import toy.two.shorturl.shortlink.application.RedirectRecordedEvent

interface RedirectEventPublisher {
    fun publish(event: RedirectRecordedEvent)
}
