package toy.two.shorturl.shortlink.application.port

interface RedirectMetricsRecorder {
    fun recordCacheHit()

    fun recordCacheMiss()

    fun recordFound()

    fun recordNotFound()

    fun recordGone()
}

object NoOpRedirectMetricsRecorder : RedirectMetricsRecorder {
    override fun recordCacheHit() = Unit

    override fun recordCacheMiss() = Unit

    override fun recordFound() = Unit

    override fun recordNotFound() = Unit

    override fun recordGone() = Unit
}
