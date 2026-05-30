package toy.two.shorturl.shortlink.application

data class RedirectEventProcessResult(
    val code: String,
    val processed: Boolean,
)

class RedirectEventProcessor {
    fun process(event: RedirectRecordedEvent): RedirectEventProcessResult =
        RedirectEventProcessResult(
            code = event.code,
            processed = true,
        )
}
