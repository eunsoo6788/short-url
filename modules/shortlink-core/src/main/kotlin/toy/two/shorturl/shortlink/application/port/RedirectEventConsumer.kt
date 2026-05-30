package toy.two.shorturl.shortlink.application.port

import toy.two.shorturl.shortlink.application.RedirectRecordedEvent

interface RedirectEventConsumer {
    fun poll(maxMessages: Int): List<RedirectRecordedEvent>
}
