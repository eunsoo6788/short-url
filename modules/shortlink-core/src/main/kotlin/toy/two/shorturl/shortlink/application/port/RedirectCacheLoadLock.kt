package toy.two.shorturl.shortlink.application.port

import toy.two.shorturl.shortlink.domain.ShortCode

interface RedirectCacheLoadLock {
    fun tryAcquire(code: ShortCode): RedirectCacheLoadLockHandle?

    fun waitForCacheFill(
        code: ShortCode,
        cacheLookup: () -> RedirectCacheEntry?,
    ): RedirectCacheEntry? = null
}

fun interface RedirectCacheLoadLockHandle : AutoCloseable {
    override fun close()
}

object NoOpRedirectCacheLoadLock : RedirectCacheLoadLock {
    override fun tryAcquire(code: ShortCode): RedirectCacheLoadLockHandle =
        RedirectCacheLoadLockHandle {}
}
