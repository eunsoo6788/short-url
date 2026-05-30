package toy.two.shorturl.redirect

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import toy.two.shorturl.shortlink.application.port.RedirectMetricsRecorder

@Component
class RedirectMicrometerMetricsRecorder(
    meterRegistry: MeterRegistry,
) : RedirectMetricsRecorder {
    private val cacheHit = cacheCounter(meterRegistry, "hit")
    private val cacheMiss = cacheCounter(meterRegistry, "miss")
    private val found = resolutionCounter(meterRegistry, "found")
    private val notFound = resolutionCounter(meterRegistry, "not_found")
    private val gone = resolutionCounter(meterRegistry, "gone")

    override fun recordCacheHit() {
        cacheHit.increment()
    }

    override fun recordCacheMiss() {
        cacheMiss.increment()
    }

    override fun recordFound() {
        found.increment()
    }

    override fun recordNotFound() {
        notFound.increment()
    }

    override fun recordGone() {
        gone.increment()
    }

    private fun cacheCounter(meterRegistry: MeterRegistry, result: String): Counter =
        Counter.builder("short_url_redirect_cache_total")
            .description("Redirect cache lookup count")
            .tag("result", result)
            .register(meterRegistry)

    private fun resolutionCounter(meterRegistry: MeterRegistry, outcome: String): Counter =
        Counter.builder("short_url_redirect_resolution_total")
            .description("Redirect resolution outcome count")
            .tag("outcome", outcome)
            .register(meterRegistry)
}
