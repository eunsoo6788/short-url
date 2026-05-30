package toy.two.shorturl.worker

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import toy.two.shorturl.shortlink.application.RedirectEventProcessor
import toy.two.shorturl.shortlink.application.port.RedirectEventConsumer

@Component
class RedirectEventPollingWorker(
    private val consumer: RedirectEventConsumer,
    private val processor: RedirectEventProcessor,
    @Value("\${short-url.worker.max-messages:10}")
    private val maxMessages: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${short-url.worker.poll-delay-ms:5000}")
    fun poll() {
        consumer.poll(maxMessages)
            .map { processor.process(it) }
            .forEach { result ->
                log.info("redirect event processed code={} processed={}", result.code, result.processed)
            }
    }
}
